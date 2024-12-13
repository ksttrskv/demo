package com.example.demo.TelegramBotPackage

import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
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
                message.text.equals(
                    "/start",
                    ignoreCase = true
                ) -> handleStartCommand(message.chatId)

                message.text.equals(
                    "/support",
                    ignoreCase = true
                ) -> handleSupportCommand(message.chatId)
            }
        } else if (update.hasCallbackQuery()) {
            val callbackQuery = update.callbackQuery
            val chatId = callbackQuery.message.chatId

            when (callbackQuery.data) {
                "готово" -> {
                    val userId = callbackQuery.from.id
                    try {
                        // Отправляем сообщение об успешной отправке фотографий
                        sendMessage(chatId, "готово")
                        // Удаляем сообщение с кнопками

                        // Создаём временное сообщение для передачи в handleReady
                        val tempMessage = Message().apply {
                            from = callbackQuery.from
                            chat = callbackQuery.message.chat
                            text = "готово"
                        }

                        // Вызываем handleReady для обработки отправки фотографий
                        handleReady(tempMessage)

                        deleteMessage(chatId, callbackQuery.message.messageId)

                    } catch (e: TelegramApiException) {
                        println("Ошибка при обработке 'готово': ${e.message}")
                        e.printStackTrace()

                        // Уведомляем пользователя об ошибке
                        sendMessage(
                            chatId,
                            "Произошла ошибка при отправке фотографий. Попробуйте снова."
                        )
                    }
                }

                "отменить" -> {
                    val userId = callbackQuery.from.id

                    try {
                        // Удаляем сохраненные данные пользователя
                        userPhotos.remove(userId)
                        userCaptions.remove(userId)

                        // Отправляем сообщение об отмене
                        sendMessage(chatId, "Отменили\\! Можете начать заново\\.")

                        // Удаляем сообщение с кнопками
                        deleteMessage(chatId, callbackQuery.message.messageId)
                    } catch (e: TelegramApiException) {
                        println("Ошибка при обработке 'отменить': ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun deleteMessage(chatId: Long, messageId: Int) {
        try {
            execute(DeleteMessage().apply {
                this.chatId = chatId.toString()
                this.messageId = messageId
            })
        } catch (e: TelegramApiException) {
            println("Ошибка при удалении сообщения: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun sendMessageWithReadyButton(chatId: Long) {
        val message = SendMessage().apply {
            this.chatId = chatId.toString()
            text = "Кликни \uD83C\uDF73, чтобы запостить или \uD83D\uDCDB чтобы отменить"

            // Добавляем кнопки "Готово" и "Отменить"
            replyMarkup = InlineKeyboardMarkup().apply {
                keyboard = listOf(
                    listOf(
                        InlineKeyboardButton("\uD83C\uDF73").apply {
                            callbackData = "готово" // Callback-данные для "Готово"
                        },
                        InlineKeyboardButton("\uD83D\uDCDB").apply {
                            callbackData = "отменить" // Callback-данные для "Отменить"
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

    private fun handleStartCommand(chatId: Long) {
        sendWelcomeMessage(chatId)
    }

    private fun handleSupportCommand(chatId: Long) {
        sendMessage(chatId, "Напиши в лс: @vksrttsk")
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

            // Очищаем хранилища
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

    private fun sendMessage(chatId: Long, text: String) {
        try {
            execute(SendMessage().apply {
                this.chatId = chatId.toString()
                this.text = if (text == "готово") {
                    "[готово](https://t.me/xruchevo207)"
                } else {
                    text
                }
                this.parseMode = "MarkdownV2"
            })
        } catch (e: TelegramApiException) {
            e.printStackTrace()
            println("Ошибка при отправке сообщения: ${e.message}")
        }
    }

    private fun sendWelcomeMessage(chatId: Long) {
        val welcomeMessage =
            "Привет! \uD83D\uDC4B Этот бот позволяет отправлять фотографии твоего хрючева в канал :)\n \nОтправь фотографии 'паком', или по одной\n \nЕсли возникли вопросы, напиши в /support: @vksrttsk"


        try {
            execute(SendMessage().apply {
                this.chatId = chatId.toString()
                this.text = welcomeMessage
                this.parseMode = "HTML"
            })
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }
}