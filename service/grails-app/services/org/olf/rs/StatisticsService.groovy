package org.olf.rs

import org.olf.rs.shared.TenantSymbolMapping
import grails.gorm.multitenancy.*
import grails.gorm.transactions.Transactional
import groovy.sql.Sql
import org.olf.rs.Counter

public class StatisticsService {

  def grailsApplication
  def dataSource

  public incrementCounter(String context) {
    Counter c = Counter.findByContext(context) ?: new Counter(context:context, value:0)
    c.value++
    c.save(flush:true, failOnError:true);
  }

  public decrementCounter(String context) {
    Counter c = Counter.findByContext(context) ?: new Counter(context:context, value:0)
    c.value--
    c.save(flush:true, failOnError:true);
  }

}
