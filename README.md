# TFTP Server

## General Description

An extended TFTP (Trivial File Transfer Protocol) server and client. 
The TFTP server allows multiple users to upload and download files and announces file additions or deletions. 
The communication uses a binary protocol supporting file transfers and lookups. 
The server is based on the Thread-Per-Client (TPC) model.

## Client Behavior

The client uses two threads: one reads input from the keyboard, and the other reads input from the socket.

### Keyboard Thread Commands

#### LOGRQ
- **Login User to the server**
- **Format**: `LOGRQ <Username>`
- **Example**: `LOGRQ KELVE YAM`

#### DELRQ
- **Delete File from the server**
- **Format**: `DELRQ <Filename>`
- **Example**: `DELRQ lehem hvita`

#### RRQ
- **Download file from the server**
- **Format**: `RRQ <Filename>`
- **Example**: `RRQ kelve yam.mp3`

#### WRQ
- **Upload File to the server**
- **Format**: `WRQ <Filename>`
- **Example**: `WRQ Operation Grandma.mp4`

#### DIRQ
- **List all file names on the server**
- **Format**: `DIRQ`

#### DISC
- **Disconnect from the server and close the program**
- **Format**: `DISC`

### Listening Thread

#### DATA Packet
- Save data to a file or buffer and send an ACK packet.

#### ACK Packet
- Print: `ACK <block number>`

#### BCAST Packet
- Print: `BCAST <del/add> <file name>`

#### Error Packet
- Print: `Error <Error number> <Error Message if exist>`

## TFTP Protocol

The extended TFTP protocol supports various commands for file transfers.

### Supported Packets

#### LOGRQ
- **Opcode**: 7
- **Parameters**: Username
- **Example**: `0, 7, <username>, 0`

#### DELRQ
- **Opcode**: 8
- **Parameters**: Filename
- **Example**: `0, 8, <filename>, 0`

#### RRQ/WRQ
- **Opcode**: 1 for RRQ, 2 for WRQ
- **Parameters**: Filename
- **Example**: `0, 1, <filename>, 0` for RRQ

#### DIRQ
- **Opcode**: 6
- **Example**: `0, 6`

#### DATA
- **Opcode**: 3
- **Parameters**: Packet Size, Block Number, Data
- **Example**: `0, 3, 0, 1a, 0, 1, <data>`

#### ACK
- **Opcode**: 4
- **Parameters**: Block Number
- **Example**: `0, 4, 0, 1`

#### BCAST
- **Opcode**: 9
- **Parameters**: Deleted/Added, Filename
- **Example**: `0, 9, 1, <filename>, 0`

#### ERROR
- **Opcode**: 5
- **Parameters**: ErrorCode, ErrMsg
- **Example**: `0, 5, 0, 7, <error message>, 0`

#### DISC
- **Opcode**: 10
- **Example**: `0, a`

