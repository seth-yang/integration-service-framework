package org.dreamwork.integration.internal;

import org.dreamwork.util.IOUtil;
import org.dreamwork.util.StringUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by seth.yang on 2019/12/5
 */
@WebServlet (urlPatterns = "/mgt/*", name = "management-servlet")
public class ManageServlet extends HttpServlet {
    @Override
    protected void doGet (HttpServletRequest request, HttpServletResponse response) throws IOException {
        if ("/favicon.ico".equals (request.getServletPath())) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream ("favicon.ico")) {
                if (in != null) {
                    response.setContentType ("image/vnd.microsoft.icon");
                    IOUtil.dump(in, response.getOutputStream());
                } else {
                    response.setStatus (HttpServletResponse.SC_NOT_FOUND);
                }
            }
            return;
        }

        String pathInfo = request.getPathInfo ();
        if (StringUtil.isEmpty (pathInfo) || "/".equals (pathInfo)) {
            response.getWriter().write(
                    "<html><body><h3><center>" +
                            "Hothink Integration Framework<br/>" +
                            "<small>Embedded Httpd Server <br/>" +
                            "v0.1</small></center></h3></body></html>"
            );
        } else {
            response.sendError (HttpServletResponse.SC_FORBIDDEN);
        }
        ClassLoader loader = getClass().getClassLoader();
        while (loader != null) {
            System.out.println (loader);
            loader = loader.getParent();
        }
    }

    @Override
    protected void doPost (HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPost (req, resp);
    }

    @Override
    protected void doDelete (HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doDelete (req, resp);
    }
}