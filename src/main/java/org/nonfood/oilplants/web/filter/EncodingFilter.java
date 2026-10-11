package org.nonfood.oilplants.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;

/** Sets request encoding and adds lightweight correlation/slow-request diagnostics. */
public class EncodingFilter implements Filter {
    public static final String REQUEST_ID_ATTRIBUTE="plantScope.requestId";
    private static final Logger LOG=Logger.getLogger(EncodingFilter.class.getName());
    private static final long SLOW_REQUEST_MS=500;

    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException {
        request.setCharacterEncoding("UTF-8");response.setCharacterEncoding("UTF-8");
        long started=System.nanoTime();
        String requestId=UUID.randomUUID().toString();
        if(request instanceof HttpServletRequest httpRequest)httpRequest.setAttribute(REQUEST_ID_ATTRIBUTE,requestId);
        if(response instanceof HttpServletResponse httpResponse)httpResponse.setHeader("X-Request-ID",requestId);
        try {
            chain.doFilter(request,response);
        } catch(IOException|ServletException|RuntimeException failure) {
            if(request instanceof HttpServletRequest httpRequest)LOG.warning("requestId="+requestId+" request failed method="+httpRequest.getMethod()+" path="+httpRequest.getRequestURI());
            throw failure;
        } finally {
            long elapsedMs=(System.nanoTime()-started)/1_000_000;
            int status=response instanceof HttpServletResponse httpResponse?httpResponse.getStatus():0;
            if(request instanceof HttpServletRequest httpRequest&&(elapsedMs>=SLOW_REQUEST_MS||status>=500))
                LOG.warning("requestId="+requestId+" method="+httpRequest.getMethod()+" path="+httpRequest.getRequestURI()+" status="+status+" elapsedMs="+elapsedMs);
        }
    }
}
