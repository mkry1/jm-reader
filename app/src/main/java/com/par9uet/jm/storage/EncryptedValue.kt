package com.par9uet.jm.storage

internal fun saveEncryptedValue(value: String, encrypt: (String) -> String, persist: (String) -> Unit) {
    val ciphertext = encrypt(value)
    require(ciphertext.startsWith("enc:")) { "当前设备无法安全加密访问凭据，未保存；可使用文件备份" }
    persist(ciphertext)
}
