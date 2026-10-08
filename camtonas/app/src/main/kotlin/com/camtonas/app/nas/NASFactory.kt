package com.camtonas.app.nas

import com.camtonas.app.data.AppSettings
import com.camtonas.app.data.NasProtocol

/** Build the right sender for the current settings. */
object NASFactory {
    fun create(settings: AppSettings): NASSender = when (settings.nasProtocol) {
        NasProtocol.SMB -> SMBSender(
            host = settings.nasHost,
            share = settings.nasShare,
            basePath = settings.nasPath.trim('/'),
            username = settings.nasUsername,
            password = settings.nasPassword,
            port = settings.nasPort,
        )
        NasProtocol.SFTP -> SFTPSender(
            host = settings.nasHost,
            port = if (settings.nasPort > 0) settings.nasPort else 22,
            username = settings.nasUsername,
            password = settings.nasPassword,
            basePath = settings.nasPath.trim('/'),
        )
        NasProtocol.FTP -> FTPSender(
            host = settings.nasHost,
            port = if (settings.nasPort > 0) settings.nasPort else 21,
            username = settings.nasUsername,
            password = settings.nasPassword,
            basePath = settings.nasPath.trim('/'),
            useTls = settings.useTls,
        )
        NasProtocol.WEBDAV -> WebDAVSender(
            host = settings.nasHost,
            port = if (settings.nasPort > 0) settings.nasPort else 0,
            username = settings.nasUsername,
            password = settings.nasPassword,
            basePath = settings.nasPath.trim('/'),
            useTls = settings.useTls,
        )
    }
}
