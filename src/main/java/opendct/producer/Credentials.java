/*
 * Copyright 2015-2016 The OpenDCT Authors. All Rights Reserved
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package opendct.producer;

import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;

public class Credentials<T> {
    private T base;
    private String username;
    private String password;

    public Credentials(T base, String username, String password) {
        this.base = base;
        this.username = username;
        this.password = password;
    }

    public T getKey() {
        return base;
    }

    protected String getUsername() {
        return username;
    }

    protected String getPassword() {
        return password;
    }

    public String getEncodedBase64()
    {
        return DatatypeConverter.printBase64Binary((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
