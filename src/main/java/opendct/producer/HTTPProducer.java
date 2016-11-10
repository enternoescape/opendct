/*
 * Copyright 2015 The OpenDCT Authors. All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opendct.producer;

import java.io.IOException;
import java.net.URL;

public interface HTTPProducer extends SageTVProducer {

    /**
     * Sets and connects to the requested URL.
     * <p/>
     * If more than one URL is provided, it is assumed that they all reference the same content and
     * it is up to the producer to determine what one to use.
     *
     * @param url This is the address to download content.
     * @throws IOException If there is a problem connecting to all provided URLs.
     */
    public void setSourceUrls(URL... url) throws IOException;

    /**
     * Sets the username and password for a specific URL.
     * <p/>
     * Authentication must be set before using {@link #setSourceUrls(URL...)} since that method
     * might try to connect to the provided addresses and will fail if credentials are needed.
     *
     * @param url The address to assign this username and password to.
     * @param credential The credentials to assign this address.
     */
    public void setAuthentication(URL url, Credentials<URL> credential);

    /**
     * Gets the URL currently being used.
     *
     * @return This is the current URL.
     */
    public URL getSource();

    /**
     * Gets all of the currently available URL's to choose from.
     *
     * @return This is current list of URL's.
     */
    public URL[] getSources();
}
