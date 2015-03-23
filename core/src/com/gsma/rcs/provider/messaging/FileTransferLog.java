/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2014 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * NOTE: This file has been modified by Sony Mobile Communications Inc.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.gsma.rcs.provider.messaging;

import com.gsma.rcs.core.content.ContentManager;
import com.gsma.rcs.core.content.MmContent;
import com.gsma.rcs.core.ims.service.im.filetransfer.FileTransferUtils;
import com.gsma.rcs.provider.LocalContentResolver;
import com.gsma.rcs.provider.fthttp.FtHttpResume;
import com.gsma.rcs.provider.fthttp.FtHttpResumeDownload;
import com.gsma.rcs.provider.fthttp.FtHttpResumeUpload;
import com.gsma.rcs.utils.ContactUtils;
import com.gsma.rcs.utils.logger.Logger;
import com.gsma.services.rcs.GroupDeliveryInfo;
import com.gsma.services.rcs.RcsService.Direction;
import com.gsma.services.rcs.RcsService.ReadStatus;
import com.gsma.services.rcs.chat.GroupChat.ParticipantStatus;
import com.gsma.services.rcs.contact.ContactId;
import com.gsma.services.rcs.filetransfer.FileTransfer.ReasonCode;
import com.gsma.services.rcs.filetransfer.FileTransfer.State;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to interface the ft table
 */
public class FileTransferLog implements IFileTransferLog {

    private static final String SELECTION_FILE_BY_T_ID = new StringBuilder(
            FileTransferData.KEY_UPLOAD_TID).append("=?").toString();

    private static final String SELECTION_BY_PAUSED_BY_SYSTEM = new StringBuilder(
            FileTransferData.KEY_STATE).append("=").append(State.PAUSED.toInt()).append(" AND ")
            .append(FileTransferData.KEY_REASON_CODE).append("=")
            .append(ReasonCode.PAUSED_BY_SYSTEM.toInt()).toString();

    private static final String SELECTION_BY_EQUAL_CHAT_ID_AND_CONTACT = new StringBuilder(
            FileTransferData.KEY_FT_ID).append("=? AND ").append(FileTransferData.KEY_CHAT_ID)
            .append("=").append(FileTransferData.KEY_CONTACT).toString();

    private static final String SELECTION_BY_QUEUED_FILE_TRANSFERS = new StringBuilder(
            FileTransferData.KEY_STATE).append("=").append(State.QUEUED.toInt()).toString();

    private static final String SELECTION_BY_QUEUED_GROUP_FILE_TRANSFERS = new StringBuilder(
            FileTransferData.KEY_CHAT_ID).append("=? AND ")
            .append(SELECTION_BY_QUEUED_FILE_TRANSFERS).toString();

    private static final String SELECTION_BY_QUEUED_ONETOONE_FILE_TRANSFERS = new StringBuilder(
            FileTransferData.KEY_CONTACT).append("=? AND ")
            .append(SELECTION_BY_QUEUED_FILE_TRANSFERS).toString();

    private static final String SELECTION_BY_INTERRUPTED_FILE_TRANSFERS = new StringBuilder(
            FileTransferData.KEY_STATE).append("=").append(State.STARTED.toInt()).append(" AND ")
            .append(FileTransferData.KEY_TRANSFERRED).append("<>")
            .append(FileTransferData.KEY_FILESIZE).toString();

    private static final String ORDER_BY_TIMESTAMP_ASC = FileTransferData.KEY_TIMESTAMP
            .concat(" ASC");

    private static final int FIRST_COLUMN_IDX = 0;

    private final LocalContentResolver mLocalContentResolver;

    private final GroupChatLog mGroupChatLog;
    private final GroupDeliveryInfoLog mGroupChatDeliveryInfoLog;
    /**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(FileTransferLog.class.getSimpleName());

    /**
     * Constructor
     * 
     * @param localContentResolver Local content resolver
     * @param groupChatLog Group chat log
     * @param groupChatDeliveryInfoLog Group chat delivery info log
     */
    /* package private */FileTransferLog(LocalContentResolver localContentResolver,
            GroupChatLog groupChatLog, GroupDeliveryInfoLog groupChatDeliveryInfoLog) {
        mLocalContentResolver = localContentResolver;
        mGroupChatLog = groupChatLog;
        mGroupChatDeliveryInfoLog = groupChatDeliveryInfoLog;
    }

    @Override
    public void addFileTransfer(String fileTransferId, ContactId contact, Direction direction,
            MmContent content, MmContent fileIcon, State state, ReasonCode reasonCode,
            long timestamp, long timestampSent) {
        if (logger.isActivated()) {
            logger.debug(new StringBuilder("Add file transfer entry: fileTransferId=")
                    .append(fileTransferId).append(", contact=").append(contact)
                    .append(", filename=").append(content.getName()).append(", size=")
                    .append(content.getSize()).append(", MIME=").append(content.getEncoding())
                    .append(", state=").append(state).append(", reasonCode=").append(reasonCode)
                    .append(", timestamp=").append(timestamp).append(", timestampSent=")
                    .append(timestampSent).toString());
        }
        ContentValues values = new ContentValues();
        values.put(FileTransferData.KEY_FT_ID, fileTransferId);
        values.put(FileTransferData.KEY_CHAT_ID, contact.toString());
        values.put(FileTransferData.KEY_CONTACT, contact.toString());
        values.put(FileTransferData.KEY_FILE, content.getUri().toString());
        values.put(FileTransferData.KEY_FILENAME, content.getName());
        values.put(FileTransferData.KEY_MIME_TYPE, content.getEncoding());
        values.put(FileTransferData.KEY_DIRECTION, direction.toInt());
        values.put(FileTransferData.KEY_TRANSFERRED, 0);
        values.put(FileTransferData.KEY_FILESIZE, content.getSize());
        if (fileIcon != null) {
            values.put(FileTransferData.KEY_FILEICON, fileIcon.getUri().toString());
        }
        values.put(FileTransferData.KEY_READ_STATUS, ReadStatus.UNREAD.toInt());
        values.put(FileTransferData.KEY_STATE, state.toInt());
        values.put(FileTransferData.KEY_REASON_CODE, reasonCode.toInt());
        values.put(FileTransferData.KEY_TIMESTAMP, timestamp);
        values.put(FileTransferData.KEY_TIMESTAMP_SENT, timestampSent);
        values.put(FileTransferData.KEY_TIMESTAMP_DELIVERED, 0);
        values.put(FileTransferData.KEY_TIMESTAMP_DISPLAYED, 0);
        mLocalContentResolver.insert(FileTransferData.CONTENT_URI, values);
    }

    @Override
    public void addOutgoingGroupFileTransfer(String fileTransferId, String chatId,
            MmContent content, MmContent thumbnail, State state, ReasonCode reasonCode,
            long timestamp, long timestampSent) {
        if (logger.isActivated()) {
            logger.debug("addOutgoingGroupFileTransfer: fileTransferId=" + fileTransferId
                    + ", chatId=" + chatId + " filename=" + content.getName() + ", size="
                    + content.getSize() + ", MIME=" + content.getEncoding());
        }
        ContentValues values = new ContentValues();
        values.put(FileTransferData.KEY_FT_ID, fileTransferId);
        values.put(FileTransferData.KEY_CHAT_ID, chatId);
        values.put(FileTransferData.KEY_FILE, content.getUri().toString());
        values.put(FileTransferData.KEY_FILENAME, content.getName());
        values.put(FileTransferData.KEY_MIME_TYPE, content.getEncoding());
        values.put(FileTransferData.KEY_DIRECTION, Direction.OUTGOING.toInt());
        values.put(FileTransferData.KEY_TRANSFERRED, 0);
        values.put(FileTransferData.KEY_FILESIZE, content.getSize());
        values.put(MessageData.KEY_READ_STATUS, ReadStatus.UNREAD.toInt());
        values.put(FileTransferData.KEY_TIMESTAMP, timestamp);
        values.put(FileTransferData.KEY_TIMESTAMP_SENT, timestampSent);
        values.put(FileTransferData.KEY_TIMESTAMP_DELIVERED, 0);
        values.put(FileTransferData.KEY_TIMESTAMP_DISPLAYED, 0);
        values.put(FileTransferData.KEY_STATE, state.toInt());
        values.put(FileTransferData.KEY_REASON_CODE, reasonCode.toInt());
        if (thumbnail != null) {
            values.put(FileTransferData.KEY_FILEICON, thumbnail.getUri().toString());
        }
        mLocalContentResolver.insert(FileTransferData.CONTENT_URI, values);

        try {
            Set<ContactId> recipients = new HashSet<ContactId>();
            for (Map.Entry<ContactId, ParticipantStatus> participant : mGroupChatLog
                    .getParticipants(chatId).entrySet()) {
                // TODO: Verify that these are the proper values to include.
                switch (participant.getValue()) {
                    case INVITE_QUEUED:
                    case INVITING:
                    case INVITED:
                    case CONNECTED:
                    case DISCONNECTED:
                        recipients.add(participant.getKey());
                    default:
                        break;
                }
            }

            for (ContactId contact : recipients) {
                mGroupChatDeliveryInfoLog.addGroupChatDeliveryInfoEntry(chatId, contact,
                        fileTransferId, GroupDeliveryInfo.Status.NOT_DELIVERED,
                        GroupDeliveryInfo.ReasonCode.UNSPECIFIED);
            }
        } catch (Exception e) {
            if (logger.isActivated()) {
                logger.error("Group file transfer with fileTransferId '" + fileTransferId
                        + "' could not be added to database!", e);
            }
            mLocalContentResolver.delete(
                    Uri.withAppendedPath(FileTransferData.CONTENT_URI, fileTransferId), null, null);
            mLocalContentResolver.delete(
                    Uri.withAppendedPath(GroupDeliveryInfoData.CONTENT_URI, fileTransferId), null,
                    null);
            /* TODO: Throw exception */
        }
    }

    @Override
    public void addIncomingGroupFileTransfer(String fileTransferId, String chatId,
            ContactId contact, MmContent content, MmContent fileIcon, State state,
            ReasonCode reasonCode, long timestamp, long timestampSent) {
        if (logger.isActivated()) {
            logger.debug(new StringBuilder("Add incoming file transfer entry: fileTransferId=")
                    .append(fileTransferId).append(", chatId=").append(chatId).append(", contact=")
                    .append(contact).append(", filename=").append(content.getName())
                    .append(", size=").append(content.getSize()).append(", MIME=")
                    .append(content.getEncoding()).append(", state=").append(state)
                    .append(", reasonCode=").append(reasonCode).append(", timestamp=")
                    .append(timestamp).append(", timestampSent=").append(timestampSent).toString());
        }
        ContentValues values = new ContentValues();
        values.put(FileTransferData.KEY_FT_ID, fileTransferId);
        values.put(FileTransferData.KEY_CHAT_ID, chatId);
        values.put(FileTransferData.KEY_FILE, content.getUri().toString());
        values.put(FileTransferData.KEY_CONTACT, contact.toString());
        values.put(FileTransferData.KEY_FILENAME, content.getName());
        values.put(FileTransferData.KEY_MIME_TYPE, content.getEncoding());
        values.put(FileTransferData.KEY_DIRECTION, Direction.INCOMING.toInt());
        values.put(FileTransferData.KEY_TRANSFERRED, 0);
        values.put(FileTransferData.KEY_FILESIZE, content.getSize());
        values.put(FileTransferData.KEY_READ_STATUS, ReadStatus.UNREAD.toInt());
        values.put(FileTransferData.KEY_STATE, state.toInt());
        values.put(FileTransferData.KEY_REASON_CODE, reasonCode.toInt());
        values.put(FileTransferData.KEY_TIMESTAMP, timestamp);
        values.put(FileTransferData.KEY_TIMESTAMP_SENT, timestampSent);
        values.put(FileTransferData.KEY_TIMESTAMP_DELIVERED, 0);
        values.put(FileTransferData.KEY_TIMESTAMP_DISPLAYED, 0);
        if (fileIcon != null) {
            values.put(FileTransferData.KEY_FILEICON, fileIcon.getUri().toString());
        }

        mLocalContentResolver.insert(FileTransferData.CONTENT_URI, values);
    }

    @Override
    public void setFileTransferStateAndReasonCode(String fileTransferId, State state,
            ReasonCode reasonCode) {
        if (logger.isActivated()) {
            logger.debug(new StringBuilder("updateFileTransferStatus: fileTransferId=")
                    .append(fileTransferId).append(", state=").append(state)
                    .append(", reasonCode=").append(reasonCode).toString());
        }

        ContentValues values = new ContentValues();
        values.put(FileTransferData.KEY_STATE, state.toInt());
        values.put(FileTransferData.KEY_REASON_CODE, reasonCode.toInt());
        if (state == State.DELIVERED) {
            values.put(FileTransferData.KEY_TIMESTAMP_DELIVERED, System.currentTimeMillis());
        } else if (state == State.DISPLAYED) {
            values.put(FileTransferData.KEY_TIMESTAMP_DISPLAYED, System.currentTimeMillis());
        }
        mLocalContentResolver.update(
                Uri.withAppendedPath(FileTransferData.CONTENT_URI, fileTransferId), values, null,
                null);
    }

    @Override
    public void markFileTransferAsRead(String fileTransferId) {
        if (logger.isActivated()) {
            logger.debug(new StringBuilder("markFileTransferAsRead  (fileTransferId=")
                    .append(fileTransferId).append(")").toString());
        }
        ContentValues values = new ContentValues();
        values.put(FileTransferData.KEY_READ_STATUS, ReadStatus.READ.toInt());
        if (mLocalContentResolver.update(
                Uri.withAppendedPath(FileTransferData.CONTENT_URI, fileTransferId), values, null,
                null) < 1) {
            /* TODO: Throw exception */
            if (logger.isActivated()) {
                logger.warn("There was no file with fileTransferId '" + fileTransferId
                        + "' to mark as read.");
            }
        }
    }

    @Override
    public void setFileTransferProgress(String fileTransferId, long currentSize) {
        ContentValues values = new ContentValues();
        values.put(FileTransferData.KEY_TRANSFERRED, currentSize);
        mLocalContentResolver.update(
                Uri.withAppendedPath(FileTransferData.CONTENT_URI, fileTransferId), values, null,
                null);
    }

    @Override
    public void setFileTransferred(String fileTransferId, MmContent content) {
        if (logger.isActivated()) {
            logger.debug("updateFileTransferUri (fileTransferId=" + fileTransferId + ") (uri="
                    + content.getUri() + ")");
        }
        ContentValues values = new ContentValues();
        values.put(FileTransferData.KEY_STATE, State.TRANSFERRED.toInt());
        values.put(FileTransferData.KEY_REASON_CODE, ReasonCode.UNSPECIFIED.toInt());
        values.put(FileTransferData.KEY_TRANSFERRED, content.getSize());
        mLocalContentResolver.update(
                Uri.withAppendedPath(FileTransferData.CONTENT_URI, fileTransferId), values, null,
                null);
    }

    @Override
    public boolean isFileTransfer(String fileTransferId) {
        Cursor cursor = null;
        try {
            cursor = mLocalContentResolver.query(
                    Uri.withAppendedPath(FileTransferData.CONTENT_URI, fileTransferId),
                    new String[] {
                        FileTransferData.KEY_FT_ID
                    }, null, null, null);

            return cursor.moveToFirst();
        } catch (Exception e) {
            if (logger.isActivated()) {
                logger.error("Exception occured while determing if it is file transfer", e);
            }
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    public void setFileUploadTId(String fileTransferId, String tId) {
        if (logger.isActivated()) {
            logger.debug("updateFileUploadTId (tId=" + tId + ") (fileTransferId=" + fileTransferId
                    + ")");
        }
        ContentValues values = new ContentValues();
        values.put(FileTransferData.KEY_UPLOAD_TID, tId);
        mLocalContentResolver.update(
                Uri.withAppendedPath(FileTransferData.CONTENT_URI, fileTransferId), values, null,
                null);
    }

    @Override
    public void setFileDownloadAddress(String fileTransferId, Uri downloadAddress) {
        if (logger.isActivated()) {
            logger.debug("updateFileDownloadAddress (downloadAddress=" + downloadAddress
                    + ") (fileTransferId=" + fileTransferId + ")");
        }
        ContentValues values = new ContentValues();
        values.put(FileTransferData.KEY_DOWNLOAD_URI, downloadAddress.toString());
        mLocalContentResolver.update(
                Uri.withAppendedPath(FileTransferData.CONTENT_URI, fileTransferId), values, null,
                null);
    }

    @Override
    public List<FtHttpResume> retrieveFileTransfersPausedBySystem() {
        Cursor cursor = null;
        try {
            cursor = mLocalContentResolver.query(FileTransferData.CONTENT_URI, null,
                    SELECTION_BY_PAUSED_BY_SYSTEM, null, ORDER_BY_TIMESTAMP_ASC);
            if (!cursor.moveToFirst()) {
                return new ArrayList<FtHttpResume>();
            }

            int sizeColumnIdx = cursor.getColumnIndexOrThrow(FileTransferData.KEY_TRANSFERRED);
            int mimeTypeColumnIdx = cursor.getColumnIndexOrThrow(FileTransferData.KEY_MIME_TYPE);
            int contactColumnIdx = cursor.getColumnIndexOrThrow(FileTransferData.KEY_CONTACT);
            int chatIdColumnIdx = cursor.getColumnIndexOrThrow(FileTransferData.KEY_CHAT_ID);
            int fileColumnIdx = cursor.getColumnIndexOrThrow(FileTransferData.KEY_FILE);
            int fileNameColumnIdx = cursor.getColumnIndexOrThrow(FileTransferData.KEY_FILENAME);
            int directionColumnIdx = cursor.getColumnIndexOrThrow(FileTransferData.KEY_DIRECTION);
            int fileTransferIdColumnIdx = cursor.getColumnIndexOrThrow(FileTransferData.KEY_FT_ID);
            int fileIconColumnIdx = cursor.getColumnIndexOrThrow(FileTransferData.KEY_FILEICON);
            int timestampColumnIdx = cursor.getColumnIndexOrThrow(FileTransferData.KEY_TIMESTAMP);
            int timestampSentColumnIdx = cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_TIMESTAMP_SENT);
            int downloadServerAddressColumnIdx = cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_DOWNLOAD_URI);
            int tIdColumnIdx = cursor.getColumnIndexOrThrow(FileTransferData.KEY_UPLOAD_TID);

            List<FtHttpResume> fileTransfers = new ArrayList<FtHttpResume>();
            do {
                long size = cursor.getLong(sizeColumnIdx);
                String mimeType = cursor.getString(mimeTypeColumnIdx);
                String fileTransferId = cursor.getString(fileTransferIdColumnIdx);
                ContactId contact = null;
                String phoneNumber = cursor.getString(contactColumnIdx);
                try {
                    contact = ContactUtils.createContactId(phoneNumber);
                } catch (Exception e) {
                    if (logger.isActivated()) {
                        logger.error("Cannot parse contact '" + phoneNumber
                                + "' for file transfer with transfer ID '" + fileTransferId + "'");
                    }
                    continue;
                }
                String chatId = cursor.getString(chatIdColumnIdx);
                String file = cursor.getString(fileColumnIdx);
                String fileName = cursor.getString(fileNameColumnIdx);
                Direction direction = Direction.valueOf(cursor.getInt(directionColumnIdx));
                String fileIcon = cursor.getString(fileIconColumnIdx);
                long timestamp = cursor.getLong(timestampColumnIdx);
                long timestampSent = cursor.getLong(timestampSentColumnIdx);
                boolean isGroup = !contact.toString().equals(chatId);
                if (direction == Direction.INCOMING) {
                    String downloadServerAddress = cursor.getString(downloadServerAddressColumnIdx);
                    MmContent content = ContentManager.createMmContent(Uri.parse(file), size,
                            fileName);
                    Uri fileIconUri = fileIcon != null ? Uri.parse(fileIcon) : null;
                    fileTransfers.add(new FtHttpResumeDownload(Uri.parse(downloadServerAddress),
                            Uri.parse(file), fileIconUri, content, contact, chatId, fileTransferId,
                            isGroup, timestamp, timestampSent));
                } else {
                    String tId = cursor.getString(tIdColumnIdx);
                    MmContent content = ContentManager.createMmContentFromMime(Uri.parse(file),
                            mimeType, size, fileName);
                    Uri fileIconUri = fileIcon != null ? Uri.parse(fileIcon) : null;
                    fileTransfers.add(new FtHttpResumeUpload(content, fileIconUri, tId, contact,
                            chatId, fileTransferId, isGroup, timestamp, timestampSent));
                }
            } while (cursor.moveToNext());
            return fileTransfers;

        } catch (SQLException e) {
            if (logger.isActivated()) {
                logger.error("Unable to retrieve resumable file transfers!", e);
            }
            return new ArrayList<FtHttpResume>();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    public FtHttpResumeUpload retrieveFtHttpResumeUpload(String tId) {
        Cursor cursor = null;
        try {
            cursor = mLocalContentResolver.query(FileTransferData.CONTENT_URI, null,
                    SELECTION_FILE_BY_T_ID, new String[] {
                        tId
                    }, null);

            if (!cursor.moveToFirst()) {
                return null;
            }
            String fileName = cursor.getString(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_FILENAME));
            long size = cursor.getLong(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_TRANSFERRED));
            String mimeType = cursor.getString(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_MIME_TYPE));
            String fileTransferId = cursor.getString(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_FT_ID));
            ContactId contact = null;
            String phoneNumber = cursor.getString(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_CONTACT));
            long timestamp = cursor.getLong(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_TIMESTAMP));
            long timestampSent = cursor.getLong(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_TIMESTAMP_SENT));
            try {
                contact = ContactUtils.createContactId(phoneNumber);
            } catch (Exception e) {
                if (logger.isActivated()) {
                    logger.error("Cannot parse contact '" + phoneNumber
                            + "' for file transfer with transfer ID '" + fileTransferId + "'");
                }
                return null;

            }
            String chatId = cursor.getString(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_CHAT_ID));
            String file = cursor.getString(cursor.getColumnIndexOrThrow(FileTransferData.KEY_FILE));
            String fileIcon = cursor.getString(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_FILEICON));
            boolean isGroup = !contact.toString().equals(chatId);
            MmContent content = ContentManager.createMmContentFromMime(Uri.parse(file), mimeType,
                    size, fileName);
            Uri fileIconUri = fileIcon != null ? Uri.parse(fileIcon) : null;
            return new FtHttpResumeUpload(content, fileIconUri, tId, contact, chatId,
                    fileTransferId, isGroup, timestamp, timestampSent);

        } catch (SQLException e) {
            if (logger.isActivated()) {
                logger.error(e.getMessage(), e);
            }
            return null;

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see com.gsma.rcs.provider.messaging.IFileTransferLog# getFileTransferData(java.lang.String,
     * java.lang.String)
     */
    private Cursor getFileTransferData(String columnName, String fileTransferId)
            throws SQLException {
        String[] projection = {
            columnName
        };
        Cursor cursor = null;
        try {
            cursor = mLocalContentResolver.query(
                    Uri.withAppendedPath(FileTransferData.CONTENT_URI, fileTransferId), projection,
                    null, null, null);
            if (cursor.moveToFirst()) {
                return cursor;
            }

            throw new SQLException(
                    "No row returned while querying for file transfer data with fileTransferId : "
                            + fileTransferId);

        } catch (RuntimeException e) {
            if (cursor != null) {
                cursor.close();
            }
            throw e;
        }
    }

    private int getDataAsInt(Cursor cursor) {
        try {
            return cursor.getInt(FIRST_COLUMN_IDX);

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private long getDataAsLong(Cursor cursor) {
        try {
            return cursor.getLong(FIRST_COLUMN_IDX);

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private String getDataAsString(Cursor cursor) {
        try {
            return cursor.getString(FIRST_COLUMN_IDX);

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see com.gsma.rcs.provider.messaging.IFileTransferLog#getFileTransferState (java.lang.String)
     */
    public State getFileTransferState(String fileTransferId) {
        if (logger.isActivated()) {
            logger.debug("Get file transfer state for ".concat(fileTransferId));
        }
        return State.valueOf(getDataAsInt(getFileTransferData(FileTransferData.KEY_STATE,
                fileTransferId)));
    }

    /*
     * (non-Javadoc)
     * @see com.gsma.rcs.provider.messaging.IFileTransferLog#
     * getFileTransferStateReasonCode(java.lang.String)
     */
    public ReasonCode getFileTransferStateReasonCode(String fileTransferId) {
        if (logger.isActivated()) {
            logger.debug("Get file transfer reason code for ".concat(fileTransferId));
        }
        return ReasonCode.valueOf(getDataAsInt(getFileTransferData(
                FileTransferData.KEY_REASON_CODE, fileTransferId)));
    }

    /*
     * (non-Javadoc)
     * @see com.gsma.rcs.provider.messaging.IFileTransferLog#
     * getfileTransferTimestamp(java.lang.String)
     */
    public long getFileTransferTimestamp(String fileTransferId) {
        if (logger.isActivated()) {
            logger.debug("Get file transfer timestamp for ".concat(fileTransferId));
        }
        return getDataAsLong(getFileTransferData(FileTransferData.KEY_TIMESTAMP, fileTransferId));
    }

    /*
     * (non-Javadoc)
     * @see com.gsma.rcs.provider.messaging.IFileTransferLog#
     * getFileTransferSentTimestamp(java.lang.String)
     */
    public long getFileTransferSentTimestamp(String fileTransferId) {
        if (logger.isActivated()) {
            logger.debug("Get file transfer sent timestamp for ".concat(fileTransferId));
        }
        return getDataAsLong(getFileTransferData(FileTransferData.KEY_TIMESTAMP_SENT,
                fileTransferId));
    }

    /*
     * (non-Javadoc)
     * @see com.gsma.rcs.provider.messaging.IFileTransferLog# isGroupFileTransfer(java.lang.String)
     */
    public boolean isGroupFileTransfer(String fileTransferId) {
        String[] projection = new String[] {
            FileTransferData.KEY_FT_ID
        };
        String[] selArgs = new String[] {
            fileTransferId
        };
        Cursor cursor = null;
        try {
            cursor = mLocalContentResolver.query(FileTransferData.CONTENT_URI, projection,
                    SELECTION_BY_EQUAL_CHAT_ID_AND_CONTACT, selArgs, null);
            /*
             * For a one-to-one file transfer, value of chatID is equal to the value of contact
             */
            return !cursor.moveToFirst();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see com.gsma.rcs.provider.messaging.IFileTransferLog#
     * getCacheableFileTransferData(java.lang.String)
     */
    public Cursor getCacheableFileTransferData(String fileTransferId) throws SQLException {
        Cursor cursor = null;
        try {
            cursor = mLocalContentResolver.query(
                    Uri.withAppendedPath(FileTransferData.CONTENT_URI, fileTransferId), null, null,
                    null, null);
            if (cursor.moveToFirst()) {
                return cursor;
            }

            throw new SQLException(
                    "No row returned while querying for file transfer data with fileTransferId : "
                            + fileTransferId);

        } catch (RuntimeException e) {
            if (cursor != null) {
                cursor.close();
            }
            throw e;
        }
    }

    @Override
    public FtHttpResume getFileTransferResumeInfo(String fileTransferId) {
        Cursor cursor = null;
        try {
            cursor = mLocalContentResolver.query(
                    Uri.withAppendedPath(FileTransferData.CONTENT_URI, fileTransferId), null, null,
                    null, null);
            if (!cursor.moveToFirst()) {
                throw new SQLException(
                        "No row returned while querying for file transfer data with fileTransferId : "
                                + fileTransferId);
            }
            String phoneNumber = cursor.getString(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_CONTACT));
            ContactId contact = phoneNumber != null ? ContactUtils.createContactId(phoneNumber)
                    : null;
            String chatId = cursor.getString(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_CHAT_ID));
            String fileUri = cursor.getString(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_FILE));
            Direction direction = Direction.valueOf(cursor.getInt(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_DIRECTION)));
            String fileIcon = cursor.getString(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_FILEICON));
            long timestamp = cursor.getLong(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_TIMESTAMP));
            long timestampSent = cursor.getLong(cursor
                    .getColumnIndexOrThrow(FileTransferData.KEY_TIMESTAMP_SENT));
            boolean isGroup = chatId.equals(phoneNumber);
            Uri file = Uri.parse(fileUri);
            MmContent content = FileTransferUtils.createMmContent(file);
            Uri fileIconUri = fileIcon != null ? Uri.parse(fileIcon) : null;
            if (direction == Direction.INCOMING) {
                String downloadServerAddress = cursor.getString(cursor
                        .getColumnIndexOrThrow(FileTransferData.KEY_DOWNLOAD_URI));
                return new FtHttpResumeDownload(Uri.parse(downloadServerAddress), file,
                        fileIconUri, content, contact, chatId, fileTransferId, isGroup, timestamp,
                        timestampSent);
            } else {
                String tId = cursor.getString(cursor
                        .getColumnIndexOrThrow(FileTransferData.KEY_UPLOAD_TID));
                return new FtHttpResumeUpload(content, fileIconUri, tId, contact, chatId,
                        fileTransferId, isGroup, timestamp, timestampSent);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /*
     * Get both queued one-to-one and group file transfers. (non-Javadoc)
     * @see com.orangelabs.rcs.provider.messaging.IFileTransferLog# getQueuedFileTransfers()
     */
    @Override
    public Cursor getQueuedFileTransfers() {
        return mLocalContentResolver.query(FileTransferData.CONTENT_URI, null,
                SELECTION_BY_QUEUED_FILE_TRANSFERS, null, ORDER_BY_TIMESTAMP_ASC);
    }

    /*
     * (non-Javadoc)
     * @see com.orangelabs.rcs.provider.messaging.IFileTransferLog#
     * dequeueFileTransfer(java.lang.String)
     */
    public void dequeueFileTransfer(String fileTransferId, long timestamp, long timestampSent) {
        ContentValues values = new ContentValues();
        values.put(FileTransferData.KEY_STATE, State.INITIATING.toInt());
        values.put(FileTransferData.KEY_REASON_CODE, ReasonCode.UNSPECIFIED.toInt());
        /* Needs to reset the timestamp as this file was originally queued and is sent only now. */
        values.put(FileTransferData.KEY_TIMESTAMP, timestamp);
        values.put(FileTransferData.KEY_TIMESTAMP_SENT, timestampSent);
        mLocalContentResolver.update(
                Uri.withAppendedPath(FileTransferData.CONTENT_URI, fileTransferId), values, null,
                null);
    }

    @Override
    public Cursor getQueuedGroupFileTransfers(String chatId) {
        String[] selectionArgs = new String[] {
            chatId
        };
        return mLocalContentResolver.query(FileTransferData.CONTENT_URI, null,
                SELECTION_BY_QUEUED_GROUP_FILE_TRANSFERS, selectionArgs, ORDER_BY_TIMESTAMP_ASC);
    }

    @Override
    public Cursor getQueuedOneToOneFileTransfers(ContactId contact) {
        String[] selectionArgs = new String[] {
            contact.toString()
        };
        return mLocalContentResolver.query(FileTransferData.CONTENT_URI, null,
                SELECTION_BY_QUEUED_ONETOONE_FILE_TRANSFERS, selectionArgs, ORDER_BY_TIMESTAMP_ASC);
    }

    @Override
    public String getFileTransferUploadTid(String fileTransferId) {
        return getDataAsString(getFileTransferData(FileTransferData.KEY_UPLOAD_TID, fileTransferId));
    }

    @Override
    public Cursor getInterruptedFileTransfers() {
        return mLocalContentResolver.query(FileTransferData.CONTENT_URI, null,
                SELECTION_BY_INTERRUPTED_FILE_TRANSFERS, null, ORDER_BY_TIMESTAMP_ASC);
    }

    public void setFileTransferStateAndTimestamps(String fileTransferId, State state,
            ReasonCode reasonCode, long timestamp, long timestampSent) {
        if (logger.isActivated()) {
            logger.debug(new StringBuilder("updateFileTransfer: fileTransferId=")
                    .append(fileTransferId).append(", state=").append(state)
                    .append(", reasonCode=").append(reasonCode).append(", timestamp=")
                    .append(timestamp).append(", timestampSent=").append(timestampSent).toString());
        }

        ContentValues values = new ContentValues();
        values.put(FileTransferData.KEY_STATE, state.toInt());
        values.put(FileTransferData.KEY_REASON_CODE, reasonCode.toInt());
        values.put(FileTransferData.KEY_TIMESTAMP, timestamp);
        values.put(FileTransferData.KEY_TIMESTAMP_SENT, timestampSent);
        mLocalContentResolver.update(
                Uri.withAppendedPath(FileTransferData.CONTENT_URI, fileTransferId), values, null,
                null);
    }
}