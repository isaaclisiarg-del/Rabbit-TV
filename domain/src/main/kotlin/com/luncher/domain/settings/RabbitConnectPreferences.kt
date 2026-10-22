package com.luncher.domain.settings

/** Private preferences and explicit service actions shared by Rabbit TV settings and receiver. */
object RabbitConnectPreferences {
    const val FILE_NAME = "rabbit_connect_v1"
    const val KEY_ENABLED = "enabled"
    const val KEY_PAIRED = "paired"
    const val KEY_STATUS = "status"
    const val KEY_PAIRING_CODE = "pairing_code"
    const val KEY_CODE_EXPIRES_AT = "code_expires_at"
    const val KEY_IP = "local_ip"
    const val SERVICE_CLASS = "com.luncher.launcher.home.RabbitConnectServerService"
    const val ACTION_START = "com.rabbit.tv.rabbitconnect.START"
    const val ACTION_STOP = "com.rabbit.tv.rabbitconnect.STOP"
    const val ACTION_TEST = "com.rabbit.tv.rabbitconnect.TEST"
    const val ACTION_REVOKE = "com.rabbit.tv.rabbitconnect.REVOKE"
}
