package org.olf.rs;

/**
 * The interface between mod-rs and any host Library Management Systems
 *
 */
public class MockHostLMSServiceImpl extends HostLMSService {

  void validatePatron(String patronIdentifier) {
  }

  /**
   *
   *
   */
  Map placeHold(String instanceIdentifier, String itemIdentifier) {
    // For NCIP2:: issue RequestItem()
    // RequestItem takes BibliographicId(A string, or name:value pair identifying an instance) or 
    // ItemId(Item)(A String, or name:value pair identifying an item)
  }

}

