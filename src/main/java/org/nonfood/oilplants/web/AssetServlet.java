package org.nonfood.oilplants.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Path;

/** Serves bundled front-end assets; the home servlet occupies the container's default mapping. */
@WebServlet("/assets/*")
public class AssetServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank() || pathInfo.indexOf('\\') >= 0
                || pathInfo.indexOf('\0') >= 0) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Path relative = Path.of(pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo).normalize();
        if (relative.isAbsolute() || relative.toString().isBlank() || relative.startsWith("..")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String resourcePath = "/assets/" + relative.toString().replace('\\', '/');
        var resource = getServletContext().getResource(resourcePath);
        if (resource == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        URLConnection connection = resource.openConnection();
        String contentType = getServletContext().getMimeType(resourcePath);
        if (contentType == null) {
            contentType = URLConnection.guessContentTypeFromName(resourcePath);
        }
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        if (contentType.startsWith("text/") || contentType.contains("javascript")) {
            response.setContentType(contentType + (contentType.contains("charset=") ? "" : ";charset=UTF-8"));
        } else {
            response.setContentType(contentType);
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "public, max-age=3600");

        long lastModified = connection.getLastModified();
        if (lastModified > 0) {
            response.setDateHeader("Last-Modified", lastModified);
            if (request.getDateHeader("If-Modified-Since") >= (lastModified / 1000 * 1000)) {
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }
        }
        long length = connection.getContentLengthLong();
        if (length >= 0) {
            response.setContentLengthLong(length);
        }
        try (var input = connection.getInputStream(); var output = response.getOutputStream()) {
            input.transferTo(output);
        }
    }
}
