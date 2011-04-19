/*******************************************************************************
 * Copyright (c) 2010, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.core;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.orion.internal.server.core.Activator;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A status that also incorporates an HTTP response code. This status is suitable
 * for throwing an exception where the appropriate HTTP response for that failure
 * is specified.
 */
public class ServerStatus extends Status {
	/**
	 * A property defining an optional status object indicating the cause
	 * of the exception.
	 */
//	private static final String PROP_CAUSE = "Cause"; //$NON-NLS-1$
	/**
	 * An integer status code. The value is specific to the component returning
	 * the exception.
	 */
	private static final String PROP_CODE = "Code"; //$NON-NLS-1$
	/**
	 * A detailed human readable error message string.
	 */
	private static final String PROP_DETAILED_MESSAGE = "DetailedMessage"; //$NON-NLS-1$
	/**
	 * The integer HTTP response code.
	 */
	private static final String PROP_HTTP_CODE = "HttpCode"; //$NON-NLS-1$
	/**
	 * A high level error message string, suitable for display to a user.
	 */
	private static final String PROP_MESSAGE = "Message"; //$NON-NLS-1$
	
	/**
	 * A property containing JSON object with data needed to handle exception
	 */
	private static final String ERROR_DATA = "ErrorData"; //$NON-NLS-1$
	
	/**
	 * A property defining a URL of a page with further details about the
	 * exception and how it can be resolved.
	 */
//	private static final String PROP_SEE_ALSO = "SeeAlso"; //$NON-NLS-1$
	/**
	 * A string representing the status severity. The value is one of the 
	 * <code>SEVERITY_*</code> constants defined in this class.
	 */
	private static final String PROP_SEVERITY = "Severity"; //$NON-NLS-1$
	private static final String SEVERITY_CANCEL = "Cancel"; //$NON-NLS-1$
	private static final String SEVERITY_ERROR = "Error"; //$NON-NLS-1$

	private static final String SEVERITY_INFO = "Info"; //$NON-NLS-1$

	private static final String SEVERITY_OK = "Ok"; //$NON-NLS-1$

	private static final String SEVERITY_WARNING = "Warning"; //$NON-NLS-1$

	private int httpCode;
	private JSONObject errorData;
	
	/**
	 * Converts a status into a server status.
	 */
	public static ServerStatus convert(IStatus status) {
		int httpCode = 200;
		if (status.getSeverity()==IStatus.ERROR || status.getSeverity()==IStatus.CANCEL)
			httpCode = 500;
		return convert(status, httpCode);
	}

	/**
	 * Converts a status into a server status.
	 */
	public static ServerStatus convert(IStatus status, int httpCode) {
		if (status instanceof ServerStatus)
			return (ServerStatus)status;
		return new ServerStatus(status, httpCode);
	}

	/**
	 * Returns a server status from a given JSON representation as produced
	 * by the {@link #toJSON()} method.
	 * @param string The string representation
	 * @return The status
	 * @throws JSONException If the provided string is not valid JSON representation of a status
	 */
	public static ServerStatus fromJSON(String string) throws JSONException {
		JSONObject object = new JSONObject(string);
		int httpCode = object.getInt(PROP_HTTP_CODE);
		int code = object.getInt(PROP_CODE);
		String message = object.getString(PROP_MESSAGE);
		int severity = fromSeverityString(object.getString(PROP_SEVERITY));
		String detailMessage = object.optString(PROP_DETAILED_MESSAGE, null);
		Exception cause = detailMessage == null ? null : new Exception(detailMessage);
		JSONObject errorData = object.optJSONObject(ERROR_DATA);
		return new ServerStatus(new Status(severity, Activator.PI_SERVER_CORE, code, message, cause), httpCode, errorData);
	}

	public ServerStatus(int severity, int httpCode, String message, Throwable exception) {
		super(severity, Activator.PI_SERVER_CORE, message, exception);
		this.httpCode = httpCode;
	}
	
	public ServerStatus(int severity, int httpCode, String message, JSONObject errorData, Throwable exception) {
		super(severity, Activator.PI_SERVER_CORE, message, exception);
		this.httpCode = httpCode;
		this.errorData = errorData;
	}

	public ServerStatus(IStatus status, int httpCode) {
		super(status.getSeverity(), status.getPlugin(), status.getCode(), status.getMessage(), status.getException());
		this.httpCode = httpCode;
	}
	
	public ServerStatus(IStatus status, int httpCode, JSONObject errorData) {
		super(status.getSeverity(), status.getPlugin(), status.getCode(), status.getMessage(), status.getException());
		this.httpCode = httpCode;
		this.errorData = errorData;
	}


	/**
	 * Returns the HTTP response code associated with this status.
	 * @return the HTTP response code associated with this status.
	 */
	public int getHttpCode() {
		return httpCode;
	}

	private String getSeverityString() {
		//note the severity string should not be translated
		switch (getSeverity()) {
			case IStatus.ERROR :
				return SEVERITY_ERROR;
			case IStatus.WARNING :
				return SEVERITY_WARNING;
			case IStatus.INFO :
				return SEVERITY_INFO;
			case IStatus.CANCEL :
				return SEVERITY_CANCEL;
			case IStatus.OK :
				return SEVERITY_OK;
		}
		return null;
	}
	
	private static int fromSeverityString(String s) {
		if (SEVERITY_ERROR.equals(s)) {
			return ERROR;
		} else if (SEVERITY_WARNING.equals(s)) {
			return WARNING;
		} else if (SEVERITY_INFO.equals(s)) {
			return INFO;
		} else if (SEVERITY_CANCEL.equals(s)) {
			return CANCEL;
		}
		return OK;
	}

	/**
	 * Returns a JSON representation of this status object. The resulting
	 * object can be converted back into a ServerStatus instance using the
	 * {@link #fromJSON(String)} factory method.
	 * @return A JSON representation of this status.
	 */
	public JSONObject toJSON() {
		JSONObject result = new JSONObject();
		try {
			result.put(PROP_HTTP_CODE, httpCode);
			result.put(PROP_CODE, getCode());
			result.put(PROP_MESSAGE, getMessage());
			result.put(PROP_SEVERITY, getSeverityString());
			if(errorData!=null){
				result.put(ERROR_DATA, errorData);
			}
			Throwable exception = getException();
			if (exception != null)
				result.put(PROP_DETAILED_MESSAGE, exception.getMessage());
			//Could also include "seeAlso" and "errorCause"
		} catch (JSONException e) {
			//can only happen if the key is null
		}
		return result;
	}
}
