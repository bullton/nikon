package com.camtonas.app.camera

/**
 * Standard PTP operation codes we use. The full list is in the PIMA 15740
 * spec; Nikon defines additional vendor codes in the 0x90xx / 0x91xx range.
 */
object PTPOp {
    // Standard operations
    const val GetDeviceInfo         = 0x1001
    const val OpenSession           = 0x1002
    const val CloseSession          = 0x1003
    const val GetStorageIDs         = 0x1004
    const val GetStorageInfo        = 0x1005
    const val GetObjectHandles      = 0x1007
    const val GetObjectInfo         = 0x1008
    const val GetObject             = 0x1009
    const val DeleteObject          = 0x100B
    const val GetDevicePropDesc     = 0x1014
    const val GetDevicePropValue    = 0x1015
    const val SetDevicePropValue    = 0x1016

    // Standard events
    const val EventObjectAdded      = 0x4002
    const val EventObjectRemoved    = 0x4003
    const val EventDevicePropChanged = 0x4006
    const val EventStorageInfoChanged = 0x400C
    const val EventCaptureComplete  = 0x400D
}

/** Common PTP response codes. */
object PTPResponse {
    const val OK                  = 0x2001
    const val GeneralError        = 0x2002
    const val SessionNotOpen      = 0x2003
    const val InvalidTransactionID = 0x2004
    const val OperationNotSupported = 0x2005
    const val ParameterNotSupported = 0x2006
    const val IncompleteTransfer  = 0x2007
    const val InvalidStorageID    = 0x2008
    const val InvalidObjectHandle = 0x2009
    const val DeviceBusy          = 0x2019
}
