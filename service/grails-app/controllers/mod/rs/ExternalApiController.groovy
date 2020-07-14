package mod.rs

import org.olf.okapi.modules.directory.DirectoryEntry

import grails.core.GrailsApplication
import grails.plugins.*
import grails.converters.JSON
import org.olf.rs.BackgroundTaskService;
import org.olf.rs.ReshareApplicationEventHandlerService
import grails.gorm.multitenancy.Tenants
import java.text.SimpleDateFormat
import groovy.xml.StreamingMarkupBuilder
import grails.gorm.multitenancy.Tenants
import groovy.util.logging.Slf4j
import org.olf.rs.Counter
import grails.gorm.multitenancy.CurrentTenant

/**
 * External Read-Only APIs for resource sharing network connectivity
 */
@Slf4j
@CurrentTenant
class ExternalApiController {

  GrailsApplication grailsApplication
  ReshareApplicationEventHandlerService reshareApplicationEventHandlerService

  def index() {
  }

  def statistics() {

    def result=[
    ]
   
    try {
      result = [
        asAt:new Date(),
        current:Counter.list().collect { [ context:it.context, value:it.value, description:it.description ] }
      ]
    }
    catch ( Exception e ) {
      result.error=e.message;
    }

    render result as JSON
  }


  def iso18626() {
    def result=[status:'ok']
    log.debug("iso18626Controller::index(${params})");

    try {
      if ( request.XML != null ) {
        org.grails.databinding.xml.GPathResultMap iso18626_msg = new org.grails.databinding.xml.GPathResultMap(request.XML);
        log.debug("GPATH MESSAGE: ${iso18626_msg}")
        String recipient;
  
        if ( iso18626_msg.request != null ) {
          log.debug("Process inbound request message");
  
          def mr = iso18626_msg.request;
          def req_result = makeDefaultReqResult(mr, "REQUEST");
  
          // Look in request.header.supplyingAgencyId for the intended recipient
          recipient = getSymbolFor(mr.header.supplyingAgencyId);
          req_result = reshareApplicationEventHandlerService.handleRequestMessage(mr);
          log.debug("result of req_request ${req_result}");
  
          def confirmationMessage = makeConfirmationMessage(delegate, req_result)
          StringWriter sw = new StringWriter();
          sw << new StreamingMarkupBuilder().bind (confirmationMessage)
          String message = sw.toString();
  
          log.debug("CONFIRMATION MESSAGE TO RETURN: ${message}")
  
          render( contentType:"text/xml" ) {
            confirmationMessage
          }
        }
        else if ( iso18626_msg.supplyingAgencyMessage != null ) {
          log.debug("Process inbound supplyingAgencyMessage message");
  
          def msam = iso18626_msg.supplyingAgencyMessage;
          def req_result = makeDefaultReqResult(msam, "SUPPLYING_AGENCY_MESSAGE");
  
          // Look in request.header.requestingAgencyId for the intended recipient
          recipient = getSymbolFor(msam.header.requestingAgencyId);
          log.debug("incoming supplying agency message for ${msam.header.requestingAgencyId} ${recipient}");
  
          req_result = reshareApplicationEventHandlerService.handleSupplyingAgencyMessage(msam);
  
          log.debug("result of req_request ${req_result}");
  
          def confirmationMessage = makeConfirmationMessage(delegate, req_result)
          StringWriter sw = new StringWriter();
          sw << new StreamingMarkupBuilder().bind (confirmationMessage)
          String message = sw.toString();
  
          log.debug("CONFIRMATION MESSAGE TO RETURN: ${message}")
  
          render( contentType:"text/xml" ) {
            confirmationMessage
          }
        }
        else if ( iso18626_msg.requestingAgencyMessage != null ) {
          log.debug("Process inbound requestingAgencyMessage message");
  
          def mram = iso18626_msg.requestingAgencyMessage;
          def req_result = makeDefaultReqResult(mram, "REQUESTING_AGENCY_MESSAGE");
  
          // Look in request.header.supplyingAgencyId for the intended recipient
          recipient = getSymbolFor(mram.header.supplyingAgencyId);
          log.debug("incoming requesting agency message for ${mram.header.supplyingAgencyId} ${recipient}");

          req_result = reshareApplicationEventHandlerService.handleRequestingAgencyMessage(mram);
          log.debug("result of req_request ${req_result}");
  
          def confirmationMessage = makeConfirmationMessage(delegate, req_result)
          StringWriter sw = new StringWriter();
          sw << new StreamingMarkupBuilder().bind (confirmationMessage)
          String message = sw.toString();
  
          log.debug("CONFIRMATION MESSAGE TO RETURN: ${message}")
  
          render( contentType:"text/xml" ) {
            confirmationMessage
          }
        }
        else {
          render(status: 400, text: 'The sent request is not valid')
        }
      }
      else {
        log.error("NO XML Supplied in request. Unable to proceed");
        render(status: 400, text: 'The sent request is not valid')
      }
    }
    catch ( Exception e ) {
      e.printStackTrace()
    }

  }

  def symbol() {
    def result=[status:'ok']
    log.debug("iso18626Controller::symbol(${params})");
    render result as JSON
  }

  private String getSymbolFor(path) {
    String result = null;
    if ( path.agencyIdType != null && path.agencyIdValue != null ) {
      result = "${path.agencyIdType.toString()}:${path.agencyIdValue.toString()}".toString()
    }
    else {
      log.error("Missing agency id type or value");
    }
    log.debug("Returning symbol : ${result}");
    return result;
  }


  void exec ( def del, Closure c ) {
    c.rehydrate(del, c.owner, c.thisObject)()
  } 


  def makeDefaultReqResult(incomingRequest, messageType) {
    def req_result = [:];
    if (messageType ==! null) {
      throw new Exception("makeDefaultReqResult expected a messageType");
      return req_result;
    } else {
      req_result.messageType = messageType
      req_result.supIdType = incomingRequest.header.supplyingAgencyId.agencyIdType
      req_result.supId = incomingRequest.header.supplyingAgencyId.agencyIdValue
      req_result.reqAgencyIdType = incomingRequest.header.requestingAgencyId.agencyIdType
      req_result.reqAgencyId = incomingRequest.header.requestingAgencyId.agencyIdValue
      req_result.reqId = incomingRequest.header.requestingAgencyRequestId
      req_result.timeRec = incomingRequest.header.timestamp
    }
    if (messageType == "SUPPLYING_AGENCY_MESSAGE") {
      req_result.reasonForMessage = incomingRequest?.messageInfo?.reasonForMessage
    }
    if (messageType == "REQUESTING_AGENCY_MESSAGE") {
      req_result.action = incomingRequest?.activeSection?.action
    }
    
    return req_result;
  }

  // This method creates the confirmation message
  def makeConfirmationMessage(def del, def req_result) {
    SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    def currentTime = dateFormatter.format(new Date())
    return {
      exec(del) {
        ISO18626Message( 'ill:version':'1.0',
                        'xmlns':'http://illtransactions.org/2013/iso18626',
                        'xmlns:ill': 'http://illtransactions.org/2013/iso18626',
                        'xmlns:xsi': 'http://www.w3.org/2001/XMLSchema-instance',
                        'xsi:schemaLocation': 'http://illtransactions.org/2013/iso18626 http://illtransactions.org/schemas/ISO-18626-v1_1.xsd' ) {
          
          switch (req_result.messageType) {
            case "REQUEST":
              requestConfirmation {
                makeConfirmationMessageBody(delegate, req_result);
              }
              break;
            case "SUPPLYING_AGENCY_MESSAGE":
              supplyingAgencyMessageConfirmation {
                makeConfirmationMessageBody(delegate, req_result);
              }
              break;
            case "REQUESTING_AGENCY_MESSAGE":
              requestingAgencyMessageConfirmation {
                makeConfirmationMessageBody(delegate, req_result);
              }
              break;
            default:
              throw new Exception ("makeConfirmationMessage expects passed req_result to contain a valid messageType")
          }
        }
      }
    }
  }

  void makeConfirmationMessageBody(def del, def req_result) {
    SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    def currentTime = dateFormatter.format(new Date())
    exec(del) {
      confirmationHeader {
        supplyingAgencyId {
          agencyIdType(req_result.supIdType)
          agencyIdValue(req_result.supId)
        }
        requestingAgencyId {
          agencyIdType(req_result.reqAgencyIdType)
          agencyIdValue(req_result.reqAgencyId)
        }
        timestamp(currentTime)
        requestingAgencyRequestId(req_result.reqId)
        timestampReceived(req_result.timeRec)
        messageStatus(req_result.status)
        if (req_result.status != "OK") {
          errorData {
            errorType(req_result.errorType)
            errorValue(req_result.errorValue)
          }
        }
        if (req_result.messageType == "SUPPLYING_AGENCY_MESSAGE") {
          reasonForMessage(req_result.reasonForMessage)
        }
        if (req_result.messageType == "REQUESTING_AGENCY_MESSAGE") {
          action(req_result.action)
        }
      }
    }
  }
}
