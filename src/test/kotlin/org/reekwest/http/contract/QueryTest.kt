package org.reekwest.http.contract

import com.natpryce.hamkrest.absent
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.throws
import org.junit.Test
import org.reekwest.http.core.Method.GET
import org.reekwest.http.core.Request
import org.reekwest.http.core.Uri.Companion.uri
import org.reekwest.http.core.contract.ContractBreach.Companion.Invalid
import org.reekwest.http.core.contract.ContractBreach.Companion.Missing
import org.reekwest.http.core.contract.Query
import org.reekwest.http.core.contract.int
import org.reekwest.http.core.get
import org.reekwest.http.core.query

class QueryTest {
    private val request = withQueryOf("/?hello=world&hello=world2")

    @Test
    fun `value present`() {
        assertThat(Query.optional("hello")(request), equalTo("world"))
        assertThat(Query.required("hello")(request), equalTo("world"))
        assertThat(Query.map { it.length }.required("hello")(request), equalTo(5))
        assertThat(Query.map { it.length }.optional("hello")(request), equalTo(5))

        val expected: List<String?> = listOf("world", "world2")
        assertThat(Query.multi.required("hello")(request), equalTo(expected))
        assertThat(Query.multi.optional("hello")(request), equalTo(expected))
    }

    @Test
    fun `value missing`() {
        assertThat(Query.optional("world")(request), absent())

        val requiredQuery = Query.required("world")
        assertThat({ requiredQuery(request) }, throws(equalTo(Missing(requiredQuery))))

        assertThat(Query.multi.optional("world")(request), equalTo(emptyList()))
        val optionalMultiQuery = Query.multi.required("world")
        assertThat({ optionalMultiQuery(request) }, throws(equalTo(Missing(optionalMultiQuery))))
    }

    @Test
    fun `invalid value`() {
        val requiredQuery = Query.map(String::toInt).required("hello")
        assertThat({ requiredQuery(request) }, throws(equalTo(Invalid(requiredQuery))))

        val optionalQuery = Query.map(String::toInt).optional("hello")
        assertThat({ optionalQuery(request) }, throws(equalTo(Invalid(optionalQuery))))

        val requiredMultiQuery = Query.map(String::toInt).multi.required("hello")
        assertThat({ requiredMultiQuery(request) }, throws(equalTo(Invalid(requiredMultiQuery))))

        val optionalMultiQuery = Query.map(String::toInt).multi.optional("hello")
        assertThat({ optionalMultiQuery(request) }, throws(equalTo(Invalid(optionalMultiQuery))))
    }

    @Test
    fun `int`() {
        val optionalQuery = Query.int().optional("hello")
        assertThat(optionalQuery(withQueryOf("/?hello=123")), equalTo(123))

        assertThat(Query.int().optional("world")(withQueryOf("/")), absent())

        val badRequest = withQueryOf("/?hello=notAnumber")
        assertThat({ optionalQuery(badRequest) }, throws(equalTo(Invalid(optionalQuery))))
    }

    @Test
    fun `sets value on request`() {
        val query = Query.required("bob")
        val withQuery = query("hello", request)
        assertThat(query(withQuery), equalTo("hello"))
    }

    @Test
    fun `can create a custom type and get and set on request`() {
        val custom = Query.map({ MyCustomBodyType(it) }, { it.value }).required("bob")

        val instance = MyCustomBodyType("hello world!")
        val reqWithQuery = custom(instance, get(""))

        assertThat(reqWithQuery.query("bob"), equalTo("hello world!"))

        assertThat(custom(reqWithQuery), equalTo(MyCustomBodyType("hello world!")))
    }

    private fun withQueryOf(value: String) = Request(GET, uri(value))

    @Test
    fun `toString is ok`() {
        assertThat(Query.required("hello").toString(), equalTo("Required query 'hello'"))
        assertThat(Query.optional("hello").toString(), equalTo("Optional query 'hello'"))
        assertThat(Query.multi.required("hello").toString(), equalTo("Required query 'hello'"))
        assertThat(Query.multi.optional("hello").toString(), equalTo("Optional query 'hello'"))
    }
}