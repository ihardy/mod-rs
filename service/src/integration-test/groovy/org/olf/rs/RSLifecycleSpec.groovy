package org.olf.rs

import grails.testing.mixin.integration.Integration
import grails.transaction.*
import static grails.web.http.HttpHeaders.*
import static org.springframework.http.HttpStatus.*
import spock.lang.*
import geb.spock.*
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import groovy.util.logging.Slf4j
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.k_int.okapi.OkapiHeaders
import spock.lang.Shared
import grails.gorm.multitenancy.Tenants

import grails.databinding.SimpleMapDataBindingSource
import grails.web.databinding.GrailsWebDataBinder
import org.olf.okapi.modules.directory.DirectoryEntry
import grails.gorm.multitenancy.Tenants
import javax.sql.DataSource
import org.grails.orm.hibernate.HibernateDatastore

@Slf4j
@Integration
@Stepwise
class RSLifecycleSpec extends GebSpec {

  @Shared
  private Map test_info = [:]

  // Auto injected by spring
  def grailsApplication
  EventPublicationService eventPublicationService
  GrailsWebDataBinder grailsWebDataBinder
  HibernateDatastore hibernateDatastore
  DataSource dataSource
  GlobalConfigService globalConfigService

  static Map request_data = [:];

  final Closure authHeaders = {
    header OkapiHeaders.TOKEN, 'dummy'
    header OkapiHeaders.USER_ID, 'dummy'
    header OkapiHeaders.PERMISSIONS, '[ "rs.admin", "rs.user", "rs.own.read", "rs.any.read"]'
  }

  final static Logger logger = LoggerFactory.getLogger(RSLifecycleSpec.class);

  def setup() {
  }

  def cleanup() {
  }

  void "Set up test tenants "(tenantid, name) {
    when:"We post a new tenant request to the OKAPI controller"
      logger.debug("Post new tenant request for ${tenantid} to ${baseUrl}_/tenant");
      def resp = restBuilder().post("${baseUrl}_/tenant") {
        header 'X-Okapi-Tenant', tenantid
        authHeaders.rehydrate(delegate, owner, thisObject)()
      }

    then:"The response is correct"
      resp.status == CREATED.value()
      logger.debug("Post new tenant request for ${tenantid} to ${baseUrl}_/tenant completed");
      // The tenant initiation step can take a few moments to complete... So wait.
      // Ideally this will be replaced with a DB op to check the DB changelog and look for completion
      Thread.sleep(5000);
      logger.debug("Done waiting for tenant initiation.. continue");

    where:
      tenantid | name
      'TestTenantG' | 'TestTenantG'
      'TestTenantH' | 'TestTenantH'
  }

  void "Test eventing"(tenant_id, entry_id, entry_uri) {
    when:"We emit a kafka event"
    logger.debug("Publish ${entry_uri}");
    eventPublicationService.publishAsJSON('modDirectory-entryChange-'+tenant_id,
        java.util.UUID.randomUUID().toString(),
        [ 'test': 'test' ] )

    then:"The response is correct"

    where:
    tenant_id | entry_id | entry_uri
    'TestTenantG' | 'TNS' | 'https://raw.githubusercontent.com/openlibraryenvironment/mod-directory/master/seed_data/TheNewSchool.json'
    'TestTenantG' | 'AC' | 'https://raw.githubusercontent.com/openlibraryenvironment/mod-directory/master/seed_data/AlleghenyCollege.json'
    'TestTenantG' | 'DIKU' | 'https://raw.githubusercontent.com/openlibraryenvironment/mod-directory/master/seed_data/DIKU.json'
  }

  void "set Up Shared Data"(symbol, tenant_id) {

    logger.debug("Set up shared data");

    when:"We register the data mapping symbols to tenants"
     globalConfigService.registerSymbolForTenant(symbol, tenant_id);
      
    then:"We are able to resolve which tenant a symbol should be routed to"
      assert tenant_id == globalConfigService.getTenantForSymbol(symbol)

    where:
      symbol|tenant_id
      'OCLC:AVL'|'TestTenantH'
      'OCLC:ZMU'|'TestTenantG'
  }

  
  
  void "Create a new request with a ROTA pointing to Allegheny College"(tenant_id, p_title, p_author, p_systemInstanceIdentifier, p_patron_id, p_patron_reference) {
    when:"post new request"
    logger.debug("Create a new request ${tenant_id} ${p_title} ${p_patron_id}");

    def req_json_data = [
      title: p_title,
      author: p_author,
      systemInstanceIdentifier: p_systemInstanceIdentifier,
      patronReference:p_patron_reference,
      patronIdentifier:p_patron_id,
      isRequester:true,
      rota:[
        [directoryId:'OCLC:AVL', rotaPosition:"0"]
      ],
      tags: [ 'RS-TESTCASE-1' ]
    ]

    String json_payload = new groovy.json.JsonBuilder(req_json_data).toString()

    RestResponse resp = restBuilder().post("${baseUrl}/rs/patronrequests") {
      header 'X-Okapi-Tenant', tenant_id
      contentType 'application/json; charset=UTF-8'
      accept 'application/json; charset=UTF-8'
      authHeaders.rehydrate(delegate, owner, thisObject)()
      json json_payload
    }
    logger.debug("CreateReqTest1 -- Response: RESP:${resp} JSON:${resp.json.id}");

    // Stash the ID
    this.request_data['RS-LIFECYCLE-TEST-00001'] = resp.json.id
    logger.debug("Created new request for with-rota test case 1. ID is : ${request_data['RS-LIFECYCLE-TEST-00001']}")


    then:"Check the return value"
    resp.status == CREATED.value()
    assert request_data['RS-LIFECYCLE-TEST-00001'] != null;

    where:
    tenant_id | p_title | p_author | p_systemInstanceIdentifier | p_patron_id | p_patron_reference
    'TestTenantG' | 'Brain of the firm' | 'Beer, Stafford' | '1234-5678-9123-4566' | '1234-5678' | 'RS-LIFECYCLE-TEST-00001'
  }

  /**
   *  Make sure that a reciprocal request has been created in TestTenantH
   */
  void "Ensure TestTenantH (OCLC:AVL) now contains a request with patronReference 'RS-LIFECYCLE-TEST-00001'"() {

    def pr = null;

    when:
      Tenants.withId('testtenanth_mod_rs') {
        waitFor(8, 1) {
          PatronRequest.withNewTransaction {
            logger.debug("Current requests for PatronRequest in testtenanth");
            logger.debug("${PatronRequest.list()}");
            pr = PatronRequest.findByPatronReference('RS-LIFECYCLE-TEST-00001')
          }

          pr != null
        }
      }
      log.debug("Found patron request ${pr} in TestTenantH");

    then:
      assert pr != null;
  }
  
  void "Wait for the new request to have state REQUEST_SENT_TO_SUPPLIER"(tenant_id, ref) {

    boolean completed = false;
    String final_state = null;

    when:"post new request"

      Tenants.withId(tenant_id.toLowerCase()+'_mod_rs') {

        waitFor(10, 1) {
          PatronRequest.withNewTransaction {
            logger.debug("waiting for request id: ${request_data['RS-LIFECYCLE-TEST-00001']} to have state REQUEST_SENT_TO_SUPPLIER")
            def r = PatronRequest.executeQuery('select count(pr) from PatronRequest as pr where pr.id = :rid and pr.state.code = :rsts', 
                                                [rid: this.request_data['RS-LIFECYCLE-TEST-00001'], rsts: 'REQUEST_SENT_TO_SUPPLIER'])[0];
  
            if(r == 1) {
              final_state = 'REQUEST_SENT_TO_SUPPLIER'
            }
            else {
              logger.debug("request id: ${request_data['RS-LIFECYCLE-TEST-00001']} - waiting for final state REQUEST_SENT_TO_SUPPLIER. ${r} matches");
            }
          }
          final_state == 'REQUEST_SENT_TO_SUPPLIER'
        }
      }

    then:"Check the return value"
      assert final_state == 'REQUEST_SENT_TO_SUPPLIER'

    where:
      tenant_id|ref
      'TestTenantG' | 'RS-T-D-0001'
  }

  void "Delete the tenants"(tenant_id, note) {

    expect:"post delete request to the OKAPI controller for "+tenant_id+" results in OK and deleted tennant"
      // Snooze
      try {
        Thread.sleep(2000);
      }
      catch ( Exception e ) {
        e.printStackTrace()
      }

      def resp = restBuilder().delete("$baseUrl/_/tenant") {
        header 'X-Okapi-Tenant', tenant_id
        authHeaders.rehydrate(delegate, owner, thisObject)()
      }

      logger.debug("completed DELETE request on ${tenant_id}");
      resp.status == NO_CONTENT.value()
      Thread.sleep(2000);

    where:
      tenant_id | note
      'TestTenantG' | 'note'
      'TestTenantH' | 'note'
  }

  RestBuilder restBuilder() {
    new RestBuilder()
  }

}

