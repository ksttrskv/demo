package com.example.demo.TelegramBotPackage

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

@Configuration
class BotConfig {

    @Bean
    fun telegramBot(): TelegramBot {
        val bot = TelegramBot("эстетика хрючки предложка \uD83C\uDF73", "7683908670:AAEEmZOEmt16_gPN9ClvBXxH2J0aTlLzyFs",-1002270329134)
        val botsApi = TelegramBotsApi(DefaultBotSession::class.java)

        try {
            // Регистрация бота
            botsApi.registerBot(bot)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }

        return bot
    }
}

//@Configuration
//class BotConfig {
//    @Bean
//    fun telegramBotsApi(bot: TelegramBot): TelegramBotsApi =
//        TelegramBotsApi(DefaultBotSession::class.java).apply {
//            registerBot(bot)
//        }
//}