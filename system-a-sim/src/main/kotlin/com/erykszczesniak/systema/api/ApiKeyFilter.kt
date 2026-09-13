package com.erykszczesniak.systema.api

import com.erykszczesniak.systema.config.SystemAProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** Simple API-key authentication, the way many SaaS source systems do it. */
@Component
class ApiKeyFilter(
    private val properties: SystemAProperties,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return PROTECTED_PREFIXES.none { path.startsWith(it) }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val provided = request.getHeader(HEADER)
        if (provided == null || provided != properties.apiKey) {
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"error":"missing or invalid $HEADER header"}""")
            return
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        const val HEADER = "X-API-Key"
        private val PROTECTED_PREFIXES = listOf("/api/", "/graphql", "/admin/")
    }
}
