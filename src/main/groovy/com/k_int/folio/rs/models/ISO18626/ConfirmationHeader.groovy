package com.k_int.folio.rs.models.ISO18626

public class ConfirmationHeader extends HeaderBase {

	/** Date / time of the message we are responding to, Format for sending and receiving is "YYYY-MM-DDThh:mm:ssZ" */
	public Date timestampReceived;

	/** The statatus of the received request */
	public MessageStatus messageStatus;

	/** The error that occurred if any */
	public ErrorData errorData;

	public ConfirmationHeader() {
	}
}
