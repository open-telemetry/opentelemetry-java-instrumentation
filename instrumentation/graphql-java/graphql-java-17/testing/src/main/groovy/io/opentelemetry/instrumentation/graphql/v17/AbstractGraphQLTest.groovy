package io.opentelemetry.instrumentation.graphql.v17

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.opentelemetry.instrumentation.test.InstrumentationSpecification

import java.util.function.UnaryOperator

abstract class AbstractGraphQLTest extends InstrumentationSpecification {
  def err = System.err

  def "test graphql-java instrumentation"() {
    given:
    def graphql = GraphQLTestUtil.graphQL('''
      type Query {
        viewer: User!
      }
      type User {
        name: String!
        innerUser: InnerUser!
      }
      type InnerUser {
        id: String!
      }
    ''', RuntimeWiring
      .newRuntimeWiring().type(
      TypeRuntimeWiring
        .newTypeWiring("Query", new UnaryOperator<TypeRuntimeWiring.Builder>() {
          @Override
          TypeRuntimeWiring.Builder apply(TypeRuntimeWiring.Builder builder) {
            return builder.dataFetcher("viewer", new DataFetcher() {
              @Override
              Object get(DataFetchingEnvironment environment) throws Exception {
                return new User("GraphQL Test", new InnerUser("a"))
              }
            })
          }
        })
    ).build())
//      .instrumentation(new GraphQLReal(openTelemetry))
//      .build()

    when:
    def result = graphql.execute('query ParseSpanQuery { a: viewer { name } b: viewer { bName: name innerUser { id } } }')

    err.println(result.data)
    def a = "Traces:\n"
    traces.forEach {
      a += "Trace:\n"
      it.sort { x, y -> x.startEpochNanos <=> y.startEpochNanos }.forEach {
        a += (it.toString() + "\n")
      }
    }
    err.println(a)
    then:
    true
    assertTraces(3) {
      trace(0, 1) {
        span(0) {
          name('graphql.parse')
        }
      }
      trace(1, 1) {
        span(0) {
          name('graphql.validate')
        }
      }
      trace(2, 7) {
        span(0) {
          name('graphql.execute')
//          attributes {
//            attribute('graphql.source', /query ParseSpanQuery[\s\S]*/)
//          }
          hasNoParent()
        }
        span(1) {
          name('graphql.resolve')
          childOf(span(0))
          attributes {
            attribute('graphql.field.type', 'User!')
            attribute('graphql.field.name', 'viewer')
          }
        }
        span(2) {
          name('graphql.resolve')
          childOf(span(0))
          attributes {
            attribute('graphql.field.type', 'String!')
            attribute('graphql.field.name', 'name')
          }
        }
        span(3) {
          name('graphql.resolve')
          childOf(span(0))
          attributes {
            attribute('graphql.field.type', 'User!')
            attribute('graphql.field.name', 'viewer')
          }
        }
        span(4) {
          name('graphql.resolve')
          childOf(span(0))
          attributes {
            attribute('graphql.field.type', 'String!')
            attribute('graphql.field.name', 'name')
          }

        }
        span(5) {
          name('graphql.resolve')
          childOf(span(0))
          attributes {
            attribute('graphql.field.type', 'InnerUser!')
            attribute('graphql.field.name', 'innerUser')
          }

        }
        span(6) {
          name('graphql.resolve')
          childOf(span(0))
          attributes {
            attribute('graphql.field.type', 'String!')
            attribute('graphql.field.name', 'id')
          }
        }
      }
    }
  }

}
