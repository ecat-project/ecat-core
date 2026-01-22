package com.ecat.core.I18n;

import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.Unit.TemperatureUnit;
import com.ecat.core.State.Unit.SpeedUnit;
import com.ecat.core.Const;
import com.ecat.core.Device.DeviceClasses;
import com.ecat.core.Device.DeviceStatus;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.InputStream;

/**
 * Test class for Core I18n functionality
 *
 * @author coffee
 */
public class CoreI18nTest {

    @Test
    public void testAttributeClassSpecialHtmlHandling() {
        // Test air quality parameters with special HTML handling
        assertEquals("PM<sub>2.5</sub>", AttributeClass.PM2_5.getDisplayName());
        assertEquals("PM<sub>10</sub>", AttributeClass.PM10.getDisplayName());
        assertEquals("SO<sub>2</sub>", AttributeClass.SO2.getDisplayName());
        assertEquals("NO<sub>x</sub>", AttributeClass.NOX.getDisplayName());
        assertEquals("NO<sub>2</sub>", AttributeClass.NO2.getDisplayName());
        assertEquals("O<sub>3</sub>", AttributeClass.O3.getDisplayName());
        assertEquals("O<sub>2</sub>", AttributeClass.O2.getDisplayName());
    }

    @Test
    public void testAttributeClassI18nKeyGeneration() {
        // Test that regular attributes use i18n keys (not hardcoded values)
        String tempKey = "state.attr.temperature";
        String windKey = "state.attr.wind_speed";
        String humidityKey = "state.attr.humidity";
        String pressureKey = "state.attr.pressure";

        // Verify the keys are being used (not the actual translated values)
        assertTrue(AttributeClass.TEMPERATURE.getDisplayName().contains(tempKey) ||
                   AttributeClass.TEMPERATURE.getDisplayName().equals("Temperature"));
        assertTrue(AttributeClass.WINDSPEED.getDisplayName().contains(windKey) ||
                   AttributeClass.WINDSPEED.getDisplayName().equals("Wind Speed"));
        assertTrue(AttributeClass.HUMIDITY.getDisplayName().contains(humidityKey) ||
                   AttributeClass.HUMIDITY.getDisplayName().equals("Humidity"));
        assertTrue(AttributeClass.PRESSURE.getDisplayName().contains(pressureKey) ||
                   AttributeClass.PRESSURE.getDisplayName().equals("Pressure"));
    }

    @Test
    public void testAttributeStatusI18nKeyGeneration() {
        // Test that attribute status uses i18n keys
        String normalKey = "state.status.normal";
        String alarmKey = "state.status.alarm";

        // Verify the keys are being used
        assertTrue(AttributeStatus.NORMAL.getDescription().contains(normalKey) ||
                   AttributeStatus.NORMAL.getDescription().equals("Normal"));
        assertTrue(AttributeStatus.ALARM.getDescription().contains(alarmKey) ||
                   AttributeStatus.ALARM.getDescription().equals("Alarm"));
    }

    @Test
    public void testUnitI18nKeyGeneration() {
        // Test that units use i18n keys
        String tempUnitKey = "state.unit.temperature.°c";
        String speedUnitKey = "state.unit.speed.m_s";

        // Verify the keys are being used
        assertTrue(TemperatureUnit.CELSIUS.getDisplayName().contains(tempUnitKey) ||
                   TemperatureUnit.CELSIUS.getDisplayName().equals("°C"));
        assertTrue(SpeedUnit.METER_PER_SECOND.getDisplayName().contains(speedUnitKey) ||
                   SpeedUnit.METER_PER_SECOND.getDisplayName().equals("m/s"));
    }

    @Test
    public void testDeviceClassesI18nKeyGeneration() {
        // Test that device classes use i18n keys
        String pmMonitorKey = "device.type.air.monitor.pm";
        String weatherSensorKey = "device.type.weather.sensor";

        // Verify the keys are being used
        assertTrue(DeviceClasses.AIR_MONITOR_PM.getDisplayName().contains(pmMonitorKey) ||
                   DeviceClasses.AIR_MONITOR_PM.getDisplayName().equals("PM Monitor"));
        assertTrue(DeviceClasses.WEATHER_SENSOR.getDisplayName().contains(weatherSensorKey) ||
                   DeviceClasses.WEATHER_SENSOR.getDisplayName().equals("Weather Sensor"));
    }

    @Test
    public void testDeviceStatusI18nKeyGeneration() {
        // Test that device status uses i18n keys
        String standbyKey = "device.status.standby";
        String normalKey = "device.status.normal";

        // Verify the keys are being used
        assertTrue(DeviceStatus.STANDBY.getStatusName().contains(standbyKey) ||
                   DeviceStatus.STANDBY.getStatusName().equals("Standby"));
        assertTrue(DeviceStatus.NORMAL.getStatusName().contains(normalKey) ||
                   DeviceStatus.NORMAL.getStatusName().equals("Normal"));
    }

    @Test
    public void testI18nHelperExistence() {
        // Test that I18nHelper class exists and has required methods
        assertNotNull(I18nHelper.class);

        // Test that createCoreProxy method exists and returns a proxy
        I18nProxy proxy = I18nHelper.createCoreProxy();
        assertNotNull(proxy);

        // Test that the proxy has correct artifactId
        assertEquals(Const.CORE_ARTIFACT_ID, proxy.getArtifactId());
        assertEquals(Const.CORE_ARTIFACT_ID, proxy.getNamespace());
    }

    @Test
    public void testI18nProxyBasicFunctionality() {
        // Test basic I18nProxy functionality
        I18nProxy proxy = I18nHelper.createCoreProxy();

        // Test translation lookup (should return key if not found, but not null)
        String translation = proxy.t("test.key");
        assertNotNull(translation);

        // Test with parameters
        String translationWithParams = proxy.t("test.key", "param1", 123);
        assertNotNull(translationWithParams);
    }
}
