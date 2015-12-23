package opendct.sagetv;

import opendct.capture.CaptureDevice;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class SageTVUnloadedDevice implements Comparable<SageTVUnloadedDevice> {
    private final Logger logger = LogManager.getLogger(SageTVUnloadedDevice.class);

    public final String ENCODER_NAME;
    public final String DESCRIPTION;
    private final Class<CaptureDevice> captureDeviceImpl;
    private final Object[] parameters;
    private final Class[] parameterTypes;
    private final boolean persist;

    /**
     * Creates a new unloaded device.
     *
     * @param encoderName This is the default name the capture device would have. For persistent
     *                    devices, this must have a number appended to it if the device is added.
     * @param captureDeviceImpl This is the capture device implementation to be used to create this
     *                          capture device.
     * @param parameters These are the initialization parameters to be used.
     * @param parameterTypes There are the initialization parameters types to be used.
     * @param persist If <i>true</i> this device will never be removed from the unloaded devices
     *                even when added.
     * @param description This is a description of the device. This should contain enough detail to
     *                    give you an idea of what you would be loading.
     */
    public SageTVUnloadedDevice(String encoderName, Class captureDeviceImpl, Object parameters[], Class parameterTypes[], boolean persist, String description) {
        this.ENCODER_NAME = encoderName;
        this.DESCRIPTION = description;
        this.captureDeviceImpl = captureDeviceImpl;
        this.parameters = parameters;
        this.parameterTypes = parameterTypes;
        this.persist = persist;
    }

    /**
     * If this is set to true then this device is never removed from the unloaded devices. Use this
     * for devices that are entirely software and therefor unlimited.
     *
     * @return <i>true</i> if this device is to always be available in the unloaded devices.
     */
    public boolean isPersistent() {
        return persist;
    }

    // This needs to be able to handle the largest possible constructor to work correctly.
    public CaptureDevice getCaptureDevice() throws IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchMethodException {

        if (!(parameters.length == parameterTypes.length)) {
            logger.error("The number of the parameter types and parameters don't match.");
            return null;
        }

        Constructor genericConstructor = captureDeviceImpl.getConstructor(parameterTypes);
        Object genericDevice = genericConstructor.newInstance(parameters);

        if (genericDevice instanceof CaptureDevice) {
            return (CaptureDevice) genericDevice;
        }

        logger.error("The object created '{}' was not a capture device.", genericDevice.getClass().getName());
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SageTVUnloadedDevice that = (SageTVUnloadedDevice) o;

        return ENCODER_NAME.equals(that.ENCODER_NAME);

    }

    @Override
    public int hashCode() {
        return ENCODER_NAME.hashCode();
    }

    @Override
    public int compareTo(SageTVUnloadedDevice o) {
        return -o.ENCODER_NAME.compareTo(ENCODER_NAME);
    }
}
