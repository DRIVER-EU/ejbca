/*************************************************************************
 *                                                                       *
 *  EJBCA Community: The OpenSource Certificate Authority                *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.ejbca.ui.web.rest.api.io.response;

import java.util.ArrayList;
import java.util.List;

import org.cesecore.certificates.endentity.EndEntityInformation;

/**
 * A container class of end entity information output.
 *
 */
public class EndEntityInfosRestResponse
{

	private List<EndEntityInformation> endEntities = new ArrayList<>();

	/**
	 * Simple constructor.
	 */
	public EndEntityInfosRestResponse()
	{
	}

	/**
	 * Returns the list of EndEntityInformation.
	 *
	 * @return list of EndEntityInformation.
	 */
	public List<EndEntityInformation> getEndEntities()
	{
		return endEntities;
	}

	/**
	 * Sets a list of EndEntityInformation.
	 *
	 * @param endEntities
	 *            list of EndEntityInformation.
	 */
	public void setEndEntityInfos(final List<EndEntityInformation> endEntities)
	{
		this.endEntities = endEntities;
	}

}
