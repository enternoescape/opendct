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

package opendct.power;

public interface PowerEventListener {
    /**
     * This method will be executed when the computer is about to enter a suspended state. Do not
     * return until you are done with withever actions are needed prior to standby.
     *
     * @see <a href="https://msdn.microsoft.com/en-us/library/windows/desktop/aa372721(v=vs.85).aspx">PBT_APMSUSPEND</a>
     */
    public void onSuspendEvent();

    /**
     * This method will be executed when the computer has resumed from suspend mode.
     *
     * @see <a href="https://msdn.microsoft.com/en-us/library/windows/desktop/aa372720(v=vs.85).aspx">PBT_APMRESUMESUSPEND</a>
     */
    public void onResumeSuspendEvent();

    /**
     * This method will be executed when the computer has resumed from entering a suspended state
     * and there are devices that did not indicate that they were ok with this. This could happen
     * because of a low battery condition on a laptop or when using a UPS. I'm not sure if you can
     * get this message on versions of Windows greater than Vista.
     *
     * @see <a href="https://msdn.microsoft.com/en-us/library/windows/desktop/aa372719(v=vs.85).aspx">PBT_APMRESUMECRITICAL</a>
     */
    public void onResumeCriticalEvent();

    /**
     * This method will be executed when the system is resuming from sleep or hibernation. This
     * event is delivered every time the system resumes and does not indicate whether a user is
     * present.
     *
     * @see <a href="https://msdn.microsoft.com/en-us/library/windows/desktop/aa372718(v=vs.85).aspx">PBT_APMRESUMEAUTOMATIC</a>
     */
    public void onResumeAutomaticEvent();

}
