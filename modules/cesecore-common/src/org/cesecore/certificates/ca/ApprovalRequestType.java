/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.cesecore.certificates.ca;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a type of approval request.
 *
 * @version $Id$
 *
 */
public enum ApprovalRequestType {
    ADDEDITENDENTITY(1, "APPROVEADDEDITENDENTITY", "Add or Edit End Entity"), 
    KEYRECOVER(2, "APPROVEKEYRECOVER", "Key Recovery"), 
    REVOCATION(3, "APPROVEREVOCATION", "Revocation"), 
    ACTIVATECA(4, "APPROVEACTIVATECA", "CA Activation");

    private final  int integerValue;
    private final String languageString;
    private final String readableString;
    private static final Map<Integer, ApprovalRequestType> reverseLookupMap = new HashMap<>();

    static {
        for(ApprovalRequestType approvalRequestType : ApprovalRequestType.values()) {
            reverseLookupMap.put(approvalRequestType.getIntegerValue(), approvalRequestType);
        }
    }

    private ApprovalRequestType(int integerValue, String languageString, String readableString) {
        this.integerValue = integerValue;
        this.languageString = languageString;
        this.readableString = readableString;
    }

    public int getIntegerValue() {
        return integerValue;
    }

    public String getLanguageString() {
        return languageString;
    }

    public static ApprovalRequestType getFromIntegerValue(int integerValue) {
        return reverseLookupMap.get(integerValue);
    }

    @Override
    public String toString() {
        return readableString;
    }
}
