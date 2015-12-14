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

package opendct.config.options;

public interface DeviceOption {
/*
The primary focus of a device option is website portability of options. Because of this and since
strings are naturally the end result of all web queries, the only way to set a value is as a String
and the only way to get a value is as a String.
 */


    /**
     * This is the internal name of the option.
     * <p/>
     * This is for internal use of the class itself. As the name suggests, this should be a property
     * name as it would be stored in the global configuration. If that is maintained, you can
     * streamline the updating of properties.
     *
     * @return Internal name of the option.
     */
    public String getProperty();

    /**
     * This is the short name of the option.
     * <p/>
     * This could be just a more cleaned up representation of the variable name. This value may be
     * displayed, so make sure it is formatted with that expectation.
     *
     * @return Returns the short name of the option.
     */
    public String getName();

    /**
     * Returns a description of the option.
     * <p/>
     * Try to keep this down to one sentence if possible. It can also contain new line characters.
     *
     * @return Returns a description of the option.
     */
    public String getDescription();

    /**
     * Set the value of this option to the provided value.
     * <p/>
     * All values are provided as strings and evaluated by this method. If the resultant value is
     * invalid, the method must throw DeviceOptionException with a description of what about the
     * provided value is wrong.
     *
     * @param values This is the new value or values.
     * @throws DeviceOptionException Throws if there was something unacceptable about the provided
     *                               value. Ensure the description of the error is clear.
     */
    public void setValue(String... values) throws DeviceOptionException;

    /**
     * Retrieves the current value of this option as a string.
     *
     * @return This is the current value of this option.
     */
    public String getValue();

    /**
     * Retrieves the current value of this option as a string array.
     * <p/>
     * For non-array types this returns an array with only one value.
     *
     * @return This is the current value of this option.
     */
    public String[] getArrayValue();

    /**
     * Retrieves a list of valid values for this option.
     * <p/>
     * If this method returns anything other than an empty array, the returned values are the only
     * valid options for this option.
     *
     * @return This is the list of valid values for this option.
     */
    public String[] getValidValues();

    /**
     * Can this option be modified?
     *
     * @return Return <i>true</i> if this option cannot be modified.
     */
    public boolean isReadOnly();

    /**
     * Get the underlying object type.
     * <p/>
     * This can be helpful in determining how to represent an option. It is still up to the
     * implementation to validate that it has received a supported value when setting.
     *
     * @return Returns an enum representing the underling type.
     */
    public DeviceOptionType getType();

    /**
     * Can multiple values be returned from and submitted into this object?
     *
     * @return <i>true</i> if multiple values can be returned from and submitted into this option.
     */
    public boolean isArray();
}
