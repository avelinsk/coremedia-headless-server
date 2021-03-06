package com.coremedia.caas.query;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class QueryRegistry implements PreparsedDocumentProvider {

  private static final Logger LOG = LoggerFactory.getLogger(QueryRegistry.class);


  // mapping from query name and view name to query definition
  private Table<String, String, QueryDefinition> queryMapping = HashBasedTable.create();

  // cache for parsed queries
  private Map<String, PreparsedDocumentEntry> preparsedQueries = new ConcurrentHashMap<>();


  QueryRegistry(@NotNull List<QueryDefinition> definitions) {
    definitions.stream()
            .peek(e -> LOG.debug("Registering query definition with name '{}' and view '{}'", e.getQueryName(), e.getViewName()))
            .forEach(item -> queryMapping.put(item.getQueryName(), item.getViewName(), item));
  }


  public boolean hasDefinition(String queryName, String viewName) {
    return queryMapping.contains(queryName, viewName);
  }

  public QueryDefinition getDefinition(String queryName, String viewName) {
    return queryMapping.get(queryName, viewName);
  }


  @Override
  public PreparsedDocumentEntry get(String queryString, Function<String, PreparsedDocumentEntry> function) {
    return preparsedQueries.computeIfAbsent(queryString, function);
  }
}
