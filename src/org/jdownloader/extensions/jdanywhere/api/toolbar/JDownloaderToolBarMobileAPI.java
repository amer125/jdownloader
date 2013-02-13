package org.jdownloader.extensions.jdanywhere.api.toolbar;

import org.appwork.remoteapi.ApiNamespace;
import org.appwork.remoteapi.ApiSessionRequired;
import org.appwork.remoteapi.RemoteAPIInterface;
import org.appwork.storage.config.annotations.AllowStorage;

@ApiNamespace("mobile/toolbar")
@ApiSessionRequired
public interface JDownloaderToolBarMobileAPI extends RemoteAPIInterface {

    @AllowStorage(value = { Object.class })
    public Object getStatus();

    public boolean startDownloads();

    public boolean stopDownloads();

    // pauses a download
    // used in iPhone-App
    public boolean pauseDownloads();

}