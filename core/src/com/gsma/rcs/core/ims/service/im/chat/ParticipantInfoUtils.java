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

package com.gsma.rcs.core.ims.service.im.chat;

import static com.gsma.rcs.utils.StringUtils.UTF8;

import com.gsma.rcs.core.ims.ImsModule;
import com.gsma.rcs.core.ims.protocol.sip.SipNetworkException;
import com.gsma.rcs.core.ims.protocol.sip.SipPayloadException;
import com.gsma.rcs.core.ims.service.im.chat.resourcelist.ResourceListDocument;
import com.gsma.rcs.core.ims.service.im.chat.resourcelist.ResourceListParser;
import com.gsma.rcs.utils.ContactUtil;
import com.gsma.rcs.utils.ContactUtil.PhoneNumber;
import com.gsma.rcs.utils.logger.Logger;
import com.gsma.services.rcs.chat.GroupChat.ParticipantStatus;
import com.gsma.services.rcs.contact.ContactId;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Utilities for ParticipantInfo
 * 
 * @author YPLO6403
 */
public class ParticipantInfoUtils {

    /**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(ParticipantInfoUtils.class
            .getSimpleName());

    /**
     * Create a set of participant info from XML
     * 
     * @param xml Resource-list document in XML
     * @param status Participant info status
     * @return the set of participants
     * @throws SipNetworkException
     * @throws SipPayloadException
     */
    public static Map<ContactId, ParticipantStatus> parseResourceList(String xml,
            ParticipantStatus status) throws SipNetworkException, SipPayloadException {
        Map<ContactId, ParticipantStatus> participants = new HashMap<ContactId, ParticipantStatus>();
        try {
            InputSource pidfInput = new InputSource(new ByteArrayInputStream(xml.getBytes(UTF8)));
            ResourceListParser listParser = new ResourceListParser(pidfInput);
            ResourceListDocument resList = listParser.getResourceList();
            if (resList != null) {
                for (String entry : resList.getEntries()) {
                    PhoneNumber number = ContactUtil.getValidPhoneNumberFromUri(entry);
                    if (number == null) {
                        continue;
                    }
                    ContactId contact = ContactUtil.createContactIdFromValidatedData(number);
                    if (!contact.equals(ImsModule.IMS_USER_PROFILE.getUsername())) {
                        participants.put(contact, status);
                        if (logger.isActivated()) {
                            logger.debug("Add participant " + contact + " to the list");
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new SipNetworkException("Can't parse resource-list document!", e);

        } catch (ParserConfigurationException e) {
            throw new SipPayloadException("Can't parse resource-list document!", e);

        } catch (SAXException e) {
            throw new SipPayloadException("Can't parse resource-list document!", e);
        }
        return participants;
    }
}