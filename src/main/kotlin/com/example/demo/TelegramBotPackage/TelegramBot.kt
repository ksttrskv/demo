package com.example.demo.TelegramBotPackage

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.util.concurrent.ConcurrentHashMap

@Service
class TelegramBot(
    @Value("\${telegram.bots[0].username}") private val botUsername: String,
    @Value("\${telegram.bots[0].token}") private val botToken: String,
    @Value("\${telegram.moderatorChatId}") private val moderatorChatId: Long // ID чата модераторов
) : TelegramLongPollingBot() {

    @PostConstruct
    fun init() {
        println("Бот запущен!")
    }

    // Хранилище фотографий для каждого пользователя
    private val userPhotos = ConcurrentHashMap<Long, MutableList<String>>()
    private val userCaptions = ConcurrentHashMap<Long, String>()

    override fun getBotUsername(): String = botUsername

    override fun getBotToken(): String = botToken

    override fun onUpdateReceived(update: Update) {
        println("Update received: $update")
        if (update.hasMessage()) {
            val message = update.message
            when {
                message.hasPhoto() -> handlePhoto(message)
                message.text.equals("готово", ignoreCase = true) -> handleReady(message)
                message.text.equals("/start", ignoreCase = true) -> sendMessage(
                    message.chatId,
                    "Привет! Можешь отправить фотки 'паком', или по одной :)"
                )
                else -> handleText(message)
            }
        } else if (update.hasCallbackQuery()) {
            val callbackQuery = update.callbackQuery
            if (callbackQuery.data == "готово") {
                // Отправляем сообщение "готово" от имени бота
                val chatId = callbackQuery.message.chatId
                sendMessage(chatId, "готово")

                // Затем вызываем обработку как обычного текстового сообщения
                val fakeMessage = Message().apply {
                    from = callbackQuery.from
                    chat = callbackQuery.message.chat
                    text = "готово"
                }
                handleReady(fakeMessage)
            }
        }
    }

    private fun handleReady(message: Message) {
        val userId = message.from.id
        val photos = userPhotos[userId]

        if (photos.isNullOrEmpty()) {
            sendMessage(message.chatId, "Сперва пришли фото хрючева")
            return
        }

        try {
            // Отправляем фотографии в модераторский чат
            if (photos.size == 1) {
                val caption = userCaptions[userId] ?: ""
                execute(SendPhoto().apply {
                    chatId = moderatorChatId.toString()
                    photo = InputFile(photos.first())
                    this.caption =
                        "Автор: <b>${message.from.firstName} ${message.from.lastName ?: ""}</b>\n<i>$caption</i>"
                    this.parseMode = "HTML"
                })
            } else {
                // Если несколько фото, добавляем подпись только к первому
                val mediaGroup = photos.take(10).mapIndexed { index, fileId ->
                    InputMediaPhoto(fileId).apply {
                        this.caption = if (index == 0) {
                            "Автор: <b>${message.from.firstName} ${message.from.lastName ?: ""}</b>\n<i>${userCaptions[userId] ?: ""}</i>"
                        } else null
                        this.parseMode = "HTML" // Указываем, что разметка в формате HTML
                    }
                }
                execute(SendMediaGroup().apply {
                    chatId = moderatorChatId.toString()
                    medias = mediaGroup
                })
            }

            // Отправляем сообщение "Посмотрим - запостим!" в чат пользователя
            sendMessage(message.chatId, "Посмотрим - запостим!")

            // Очищаем данные о пользователе
            userPhotos.remove(userId)
            userCaptions.remove(userId)
        } catch (e: TelegramApiException) {
            sendMessage(message.chatId, "Произошла ошибка при отправке фотографий. ${e.message}")
            e.printStackTrace()
        }
    }

    private fun handlePhoto(message: Message) {
        val userId = message.from.id
        val photoId = message.photo.last().fileId
        val caption = message.caption ?: "" // Подпись из сообщения, если она есть

        // Сохраняем фото и его подпись
        userPhotos.computeIfAbsent(userId) { mutableListOf() }.add(photoId)
        if (caption.isNotEmpty()) {
            userCaptions[userId] = caption // Сохраняем подпись, если она присутствует
        }

        // Если это первое фото, отправляем сообщение один раз
        if (userPhotos[userId]?.size == 1) {
            sendMessageWithReadyButton(message.chatId) // сообщение с кнопкой "отправить"
        }
    }

    private fun sendMessageWithReadyButton(chatId: Long) {
        val message = SendMessage().apply {
            this.chatId = chatId.toString()
            text = "Отправь ещё или нажми 'отправить', чтобы запостить \uD83C\uDF73"

            // Добавляем кнопку "Готово"
            replyMarkup = InlineKeyboardMarkup().apply {
                keyboard = listOf(
                    listOf(
                        InlineKeyboardButton("отправить").apply {
                            callbackData = "готово" // Callback-данные при нажатии
                        }
                    )
                )
            }
        }

        try {
            execute(message)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    private fun sendMessage(chatId: Long, text: String) {
        try {
            execute(SendMessage(chatId.toString(), text))
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    private fun handleText(message: Message) {
        val userId = message.from.id
        val text = message.text

        // Сохраняем текст
        userCaptions[userId] = text
        sendMessage(
            message.chatId,
            "Ваш текст сохранён. Отправьте фото или напишите 'готово'."
        )
    }
}