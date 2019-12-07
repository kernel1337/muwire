package com.muwire.webui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.muwire.core.SharedFile;
import com.muwire.core.files.FileListCallback;

import net.i2p.data.Base64;
import net.i2p.data.DataHelper;

public class FilesServlet extends HttpServlet {
    
    private FileManager fileManager;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String section = req.getParameter("section");
        if (section == null) {
            resp.sendError(403, "Bad section param");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version='1.0' encoding='UTF-8'?>");
        if (section.equals("status")) {
            sb.append("<Status>");
            sb.append("<Count>").append(fileManager.numSharedFiles()).append("</Count>");
            sb.append("<Revision>").append(fileManager.getRevision()).append("</Revision>");
            String hashingFile = fileManager.getHashingFile();
            if (hashingFile != null)
                sb.append("<Hashing>").append(Util.escapeHTMLinXML(hashingFile)).append("</Hashing>");
            sb.append("</Status>");
        } else if (section.equals("files")) {
            sb.append("<Files>");
            sb.append("<Revision>").append(fileManager.getRevision()).append("</Revision>");
            
            ListCallback cb = new ListCallback(sb);
            String encodedPath = req.getParameter("path");
            File current = null;
            if (encodedPath != null && encodedPath.length() > 0) {
                String[] split = DataHelper.split(encodedPath, ",");
                for (String element : split) {
                    element = Base64.decodeToString(element);
                    if (element == null) {
                        resp.sendError(403, "bad path");
                        return;
                    }
                    if (current == null) {
                        current = new File(element);
                        continue;
                    }
                    current = new File(current, element);
                }
            }
            fileManager.list(current, cb);
            sb.append("</Files>");
        }
        resp.setContentType("text/xml");
        resp.setCharacterEncoding("UTF-8");
        resp.setDateHeader("Expires", 0);
        resp.setHeader("Pragma", "no-cache");
        resp.setHeader("Cache-Control", "no-store, max-age=0, no-cache, must-revalidate");
        byte[] out = sb.toString().getBytes("UTF-8");
        resp.setContentLength(out.length);
        resp.getOutputStream().write(out);
    }

    @Override
    public void init(ServletConfig cfg) throws ServletException {
        fileManager = (FileManager) cfg.getServletContext().getAttribute("fileManager");
    }

    private static class ListCallback implements FileListCallback<SharedFile> {
        private final StringBuilder sb;
        ListCallback(StringBuilder sb) {
            this.sb = sb;
        }
        @Override
        public void onFile(File f, SharedFile value) {
            sb.append("<File>");
            sb.append("<Name>").append(Util.escapeHTMLinXML(f.getName())).append("</Name>");
            sb.append("<Size>").append(DataHelper.formatSize2Decimal(value.getCachedLength())).append("B").append("</Size>");
            // TODO: other stuff
            sb.append("</File>");
        }
        @Override
        public void onDirectory(File f) {
            String name = f.getName().isEmpty() ? f.toString() : f.getName();
            sb.append("<Directory>").append(Util.escapeHTMLinXML(name)).append("</Directory>");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String action = req.getParameter("action");
        if (action.equals("share")) {
            String file = req.getParameter("file");
            fileManager.share(file);
            resp.sendRedirect("/MuWire/Files.jsp");
        } else if (action.equals("unshare")) {
            String pathElements = req.getParameter("path");
            File current = null;
            for (String element : pathElements.split(",")) {
                element = Util.unescapeHTMLinXML(Base64.decodeToString(element));
                if (current == null)
                    current = new File(element);
                else 
                    current = new File(current, element);
            }
            fileManager.unshareFile(current);
        }
    }
}