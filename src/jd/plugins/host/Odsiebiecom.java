//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team jdownloader@freenet.de
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.host;

import java.io.File;
import java.net.URL;
import java.util.regex.Pattern;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.HTTPConnection;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.HTTP;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginForHost;
import jd.plugins.RequestInfo;
import jd.plugins.download.RAFDownload;

public class Odsiebiecom extends PluginForHost {
    private static final String CODER = "JD-Team";

    private String captchaCode;
    private File captchaFile;
    private String downloadcookie;
    private String downloadurl;
    private String referrerurl;
    private RequestInfo requestInfo;

    public Odsiebiecom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://share-online.biz/rules.php";
    }

    @Override
    public String getCoder() {
        return CODER;
    }

    @Override
    public boolean getFileInformation(DownloadLink downloadLink) {
        referrerurl = downloadurl = downloadLink.getDownloadURL();
        try {
            requestInfo = HTTP.getRequest(new URL(downloadurl));
            if (requestInfo != null && requestInfo.getLocation() == null) {
                String filename = requestInfo.getRegexp("Nazwa pliku: <strong>(.*?)</strong>").getMatch(0);
                String filesize;
                if ((filesize = requestInfo.getRegexp("Rozmiar pliku: <strong>(.*?)MB</strong>").getMatch(0)) != null) {
                    downloadLink.setDownloadSize((int) Math.round(Double.parseDouble(filesize) * 1024 * 1024));
                } else if ((filesize = requestInfo.getRegexp("Rozmiar pliku: <strong>(.*?)KB</strong>").getMatch(0)) != null) {
                    downloadLink.setDownloadSize((int) Math.round(Double.parseDouble(filesize) * 1024));
                }
                downloadLink.setName(filename);
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        downloadLink.setAvailable(false);
        return false;
    }

    @Override
    public String getVersion() {
        String ret = new Regex("$Revision$", "\\$Revision: ([\\d]*?) \\$").getMatch(0);
        return ret == null ? "0.0" : ret;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception {
        LinkStatus linkStatus = downloadLink.getLinkStatus();

        /* Nochmals das File überprüfen */
        if (!getFileInformation(downloadLink)) {
            linkStatus.addStatus(LinkStatus.ERROR_FILE_NOT_FOUND);
            return;
        }
        /*
         * Zuerst schaun ob wir nen Button haben oder direkt das File vorhanden
         * ist
         */
        String steplink = requestInfo.getRegexp("<a href=\"/pobierz/(.*?)\"  style=\"font-size: 18px\">(.*?)</a>").getMatch(0);
        if (steplink == null) {
            /* Kein Button, also muss der Link irgendwo auf der Page sein */
            /* Film,Mp3 */
            downloadurl = requestInfo.getRegexp("<PARAM NAME=\"FileName\" VALUE=\"(.*?)\"").getMatch(0);
            /* Flash */
            if (downloadurl == null) {
                downloadurl = requestInfo.getRegexp("<PARAM NAME=\"movie\" VALUE=\"(.*?)\"").getMatch(0);
            }
            /* Bilder, Animationen */
            if (downloadurl == null) {
                downloadurl = requestInfo.getRegexp("onLoad=\"scaleImg\\('thepic'\\)\" src=\"(.*?)\" \\/").getMatch(0);
            }
            /* kein Link gefunden */
            if (downloadurl == null) {
                linkStatus.addStatus(LinkStatus.ERROR_RETRY);
                return;
            }
        } else {
            /* Button folgen, schaun ob Link oder Captcha als nächstes kommt */
            downloadurl = "http://odsiebie.com/pobierz/" + steplink + ".html";
            downloadcookie = requestInfo.getCookie();
            requestInfo = HTTP.getRequest(new URL(downloadurl), requestInfo.getCookie(), referrerurl, false);
            /* Das Cookie wird überschrieben, daher selbst zusammenbauen */
            downloadcookie = downloadcookie + requestInfo.getCookie();
            referrerurl = downloadurl;
            if (requestInfo.getLocation() != null) {
                /* Weiterleitung auf andere Seite, evtl mit Captcha */
                downloadurl = requestInfo.getLocation();
                requestInfo = HTTP.getRequest(new URL(downloadurl), requestInfo.getCookie(), referrerurl, false);
                downloadcookie = requestInfo.getCookie();
                referrerurl = downloadurl;
            }
            if (new Regex(requestInfo.getHtmlCode(), Pattern.compile("<img src=\"(.*?captcha.*?)\">", Pattern.CASE_INSENSITIVE)).matches()) {
                /* Captcha File holen */
                String captchaurl = new Regex(requestInfo.getHtmlCode(), Pattern.compile("<img src=\"(.*?captcha.*?)\">", Pattern.CASE_INSENSITIVE)).getMatch(0);
                captchaFile = getLocalCaptchaFile(this);
                HTTPConnection captcha_con = new HTTPConnection(new URL(captchaurl).openConnection());
                captcha_con.setRequestProperty("Referer", referrerurl);
                captcha_con.setRequestProperty("Cookie", downloadcookie);
                if (captcha_con.getContentType().contains("text")) {
                    /* Fehler beim Captcha */
                    logger.severe("Captcha Download fehlgeschlagen!");
                    // step.setStatus(PluginStep.STATUS_ERROR);
                    linkStatus.addStatus(LinkStatus.ERROR_CAPTCHA);
                    return;
                }
                Browser.download(captchaFile, captcha_con);
                /* CaptchaCode holen */
                if ((captchaCode = Plugin.getCaptchaCode(captchaFile, this)) == null) {
                    linkStatus.addStatus(LinkStatus.ERROR_CAPTCHA);
                    return;
                }
                /* Überprüfen(Captcha,Password) */
                downloadurl = "http://odsiebie.com/pobierz/" + steplink + ".html?captcha=" + captchaCode;
                requestInfo = HTTP.getRequest((new URL(downloadurl)), downloadcookie, referrerurl, false);
                if (requestInfo.getLocation() != null && requestInfo.getLocation().contains("html?err")) {
                    linkStatus.addStatus(LinkStatus.ERROR_CAPTCHA);
                    return;
                }
                downloadcookie = downloadcookie + requestInfo.getCookie();
            }
            /* DownloadLink suchen */
            steplink = requestInfo.getRegexp("<a href=\"/download/(.*?)\"").getMatch(0);
            if (steplink == null) {
                linkStatus.addStatus(LinkStatus.ERROR_RETRY);
                return;
            }
            downloadurl = "http://odsiebie.com/download/" + steplink;
            requestInfo = HTTP.getRequest(new URL(downloadurl), downloadcookie, referrerurl, false);
            if (requestInfo.getLocation() == null || requestInfo.getLocation().contains("upload")) {
                linkStatus.addStatus(LinkStatus.ERROR_RETRY);
                return;
            }
            downloadurl = requestInfo.getLocation();
            if (downloadurl == null) {
                linkStatus.addStatus(LinkStatus.ERROR_RETRY);
                return;
            }
        }
        /*
         * Leerzeichen müssen durch %20 ersetzt werden!!!!!!!!, sonst werden sie
         * von new URL() abgeschnitten
         */
        downloadurl = downloadurl.replaceAll(" ", "%20");
        /* Datei herunterladen */
        requestInfo = HTTP.getRequestWithoutHtmlCode(new URL(downloadurl), requestInfo.getCookie(), referrerurl, false);
        HTTPConnection urlConnection = requestInfo.getConnection();
        if (urlConnection.getContentLength() == 0) {
            linkStatus.addStatus(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE);
            linkStatus.setValue(20 * 60 * 1000l);
            return;
        }
        dl = new RAFDownload(this, downloadLink, urlConnection);
        dl.setChunkNum(1);
        dl.setResume(false);
        dl.startDownload();
    }

    public int getMaxSimultanFreeDownloadNum() {
        return 20;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetPluginGlobals() {

    }

}
