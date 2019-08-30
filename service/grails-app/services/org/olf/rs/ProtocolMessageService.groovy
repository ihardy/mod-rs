package org.olf.rs

import grails.gorm.multitenancy.Tenants
import java.util.UUID
/**
 * Allow callers to request that a protocol message be sent to a remote (Or local) service. Callers
 * provide the requesting and responding symbol and the content of the message, this service works out
 * the most appropriate method/protocol. Initially this will always be loopback.
 *
 */
class ProtocolMessageService {
  ReshareApplicationEventHandlerService reshareApplicationEventHandlerService
  GlobalConfigService globalConfigService
  /**
   * @param eventData : A map structured as followed 
   *   event: {
   *     envelope:{
   *       sender:{
   *         symbol:''
   *       }
   *       recipient:{
   *         symbol:''
   *       }
   *       messageType:''
   *       messageBody:{
   *       }
   *   }
   *
   * @return a map containing properties including any confirmationId the underlying protocol implementation provides us
   *
   */
  public Map sendProtocolMessage(Map eventData) {
    def responseConfirmed = messageConfirmation(eventData, "request")
    log.debug("sendProtocolMessage called for ${eventData.payload.id}")
    //Make this create a new request in the responder's system
    String confirmation = null;

    // The first thing to do is to look in the internal SharedConfig to see if the recipient is a
    // tenant in this system. If so, we can simply call handleIncomingMessage
    def req = reshareApplicationEventHandlerService.delayedGet(eventData.payload.id);
    log.debug("Got request in ProtocolMessageService ${req}");
    def symbol = req.rota[0].directoryId
    def tenant = globalConfigService.getTenantForSymbol(symbol)
    log.debug("The tenant for that symbol is: ${tenant}")
    
    if (tenant != null) {
      handleIncomingMessage(eventData)
        
    } else {
      log.error("Tenant does not exist in the system")
    }
    
    return [
      confirmationId:confirmation
    ]
  }

  /**
   * @param eventData Symmetrical with the section above. See para on sendProtocolMessage - Map should have exactly the same shape
   * @return a confirmationId
   */
  public Map handleIncomingMessage(Map eventData) {
    // Recipient must be a tenant in the SharedConfig
    log.debug("handleIncomingMessage called.")
    
    def req = reshareApplicationEventHandlerService.delayedGet(eventData.payload.id);
    def symbol = req.rota[0].directoryId
    def tenant = globalConfigService.getTenantForSymbol(symbol)
    // Now we issue a protcolMessageIndication event so that any handlers written for the protocol message can be 
    // called - this method should not do any work beyond understanding what event needs to be dispatched for the 
    // particular message coming in.
    
    if (tenant != null) {
      switch ( eventData.messageType ) {
        case 'request' :
        eventPublicationService.publishAsJSON(title: req.title, isRequester: false)
      }
    }
    
    
    
    return [
      confirmationId: UUID.randomUUID().toString()
    ]
  }

  public messageConfirmation(eventData, messageType) {
    //TODO make this able to return a confirmation message if request/supplying agency message/requesting agency message are successful,
    //and returning error messages if not
  }
}
