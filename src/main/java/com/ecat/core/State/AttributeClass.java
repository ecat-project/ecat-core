package com.ecat.core.State;

import com.ecat.core.I18n.I18nHelper;
import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.Unit.AirVolumeUnit;
import com.ecat.core.State.Unit.CurrentUnit;
import com.ecat.core.State.Unit.LiterFlowUnit;
import com.ecat.core.State.Unit.PressureUnit;
import com.ecat.core.State.Unit.SpeedUnit;
import com.ecat.core.State.Unit.TemperatureUnit;
import com.ecat.core.State.Unit.VoltageUnit;
import com.ecat.core.State.Unit.WeightUnit;

/** 
 * Attribute enum class
 * 
 * @author coffee
 */ 
public enum AttributeClass {

    // 空气质量参数 (特殊处理，包含HTML标签)
    PM2_5("PM2_5", "PM2_5", new Class[]{AirMassUnit.class,}),
    PM10("PM10", "PM10", new Class[]{AirMassUnit.class}),
    PM("PM", "pm", new Class[]{AirMassUnit.class}), // 颗粒物通用标签，适合一个device同时兼容PM2.5和PM10
    SO2("SO2", "SO2", new Class[]{AirMassUnit.class, AirVolumeUnit.class}),
    NO("NO", "no", new Class[]{AirVolumeUnit.class}),
    NOX("NOX", "nox", new Class[]{AirVolumeUnit.class}),
    NO2("NO2", "NO2", new Class[]{AirVolumeUnit.class}),
    CO("CO", "co", new Class[]{AirVolumeUnit.class}),
    O3("O3", "O3", new Class[]{AirVolumeUnit.class}),
    O2("O2", "O2", new Class[]{AirVolumeUnit.class}),
    // 通用值
    VALUE("VALUE", "value", new Class[]{}),
    NUMERIC("Numeric", "numeric", new Class[]{}), // 通用数值类型，可能是空气质量参数，也可能是其他物理量


    // 仪器自身参数
    INTERNAL_T("InternalTemp", "internal_temp", new Class[]{TemperatureUnit.class}),
    FLOW("Flow", "flow", new Class[]{LiterFlowUnit.class}),
    GAS_P("GasPressure", "gas_pressure", new Class[]{PressureUnit.class}),
    GAS_T("GasT", "gas_temperature", new Class[]{TemperatureUnit.class}),
    CHAMBER_T("ChamberT", "chamber_temperature", new Class[]{TemperatureUnit.class}),
    BENCH_T("BenchT", "bench_temperature", new Class[]{TemperatureUnit.class}),
    CHAMBER_P("ChamberP", "chamber_pressure", new Class[]{PressureUnit.class}),
    SAMPLE_P("SampleP", "sample_pressure", new Class[]{PressureUnit.class}),
    OVEN_T("OvenT", "oven_temperature", new Class[]{TemperatureUnit.class}),
    PUMP_P("PumpP", "pump_pressure", new Class[]{PressureUnit.class}),
    BOX_T("BoxTEMP", "box_temperature", new Class[]{}),
    SYSTEM_STATE("SystemState", "system_state", new Class[]{}),

    // 负载相关
    LOAD("Load", "load", new Class[]{}),
    // 电力相关
    VOLTAGE("Voltage", "voltage", new Class[]{VoltageUnit.class}),
    // 电力相关
    LINE_VOLTAGE("LineVoltage", "line_voltage", new Class[]{VoltageUnit.class}),
    CURRENT("Current", "current", new Class[]{CurrentUnit.class}),
    POWER("Power", "power", new Class[]{}),
    REACTIVE_POWER("ReactivePower", "reactive_power", new Class[]{}),
    APPARENT_POWER("ApparentPower", "apparent_power", new Class[]{}),
    POWER_FACTOR("PowerFactor", "power_factor", new Class[]{}),
    FREQUENCY("Frequency", "frequency", new Class[]{}),

    // 气象相关
    TEMPERATURE("Temperature", "temperature", new Class[]{TemperatureUnit.class}), // 温度
    HUMIDITY("Humidity", "humidity", new Class[]{}), // 湿度
    PRESSURE("Pressure", "pressure", new Class[]{PressureUnit.class}), // 压力
    WINDSPEED("WindSpeed", "wind_speed", new Class[]{SpeedUnit.class}), // 风速
    WIND_DIRECTION("WindDirection", "wind_direction", new Class[]{}), // 风向
    NOISE("Noise", "noise", new Class[]{}), // 噪声
    RAINFALL("Rainfall", "rainfall", new Class[]{}), // 降雨量
    ILLUMINANCE("Illuminance", "illuminance", new Class[]{}),  // 光照强度
    // 阳光
    SOLAR("Solar", "solar", new Class[]{}),  // 太阳类，阳光类

    // 物理相关
    DIRECTION("Direction", "direction", new Class[]{}), // 方向
    VIBRATION("Vibration", "vibration", new Class[]{}), // 震动

    // 数学相关
    PERCENTAGE("Percentage", "percentage", new Class[]{}), // 百分比

    // 位置
    ADDRESS("Address", "address", new Class[]{}), // 地址

    // 时间
    TIME("Time", "time", new Class[]{}),  // 时间类型

    // 重量
    WEIGHT("Weight", "weight", new Class[]{WeightUnit.class}),  // 重量单位，可能是克、千克等

    // 文本
    TEXT("Text", "text", new Class[]{}),  // 文本类型

    COMMAND("Command", "command", new Class[]{}),  // 命令类型

    // 空气污染物气体
    OTHER_GAS_CONCENTRATION("OtherGasConcentration", "other_gas_concentration", new Class[]{AirVolumeUnit.class}),  // 校准仪生成气体浓度，比如CO、SO2、NO
    SO2_STD_GAS_CONCENTRATION("SO2StdGasConcentration", "so2_std_gas_concentration", new Class[]{AirVolumeUnit.class}), // SO2标气浓度
    NO_STD_GAS_CONCENTRATION("NOStdGasConcentration", "no_std_gas_concentration", new Class[]{AirVolumeUnit.class}), // NO标气浓度
    CO_STD_GAS_CONCENTRATION("COStdGasConcentration", "co_std_gas_concentration", new Class[]{AirVolumeUnit.class}), // CO标气浓度
    CALIBRATOR_GAS_SELECT("CalibratorGasSelect", "calibrator_gas_select", new Class[]{}), // 生成样气种类
    O3_GAS_CONCENTRATION("O3GasConcentration", "o3_gas_concentration", new Class[]{AirVolumeUnit.class}), // O3样气浓度

    // 状态
    GENERAL_ALARM("GeneralAlarm", "general_alarm", new Class[]{}), // 通用报警
    MAINTENANCE("Maintenance", "maintenance", new Class[]{}), // 维护状态
    WARM_UP("WarmUp", "warm_up", new Class[]{}), // 热机状态
    LEAK_STATUS("LeakStatus", "leak_status", new Class[]{}), // 泄漏状态
    SAMPLING_STATUS("SamplingStatus", "sampling_status", new Class[]{}), // 采样状态
    STATUS("Status", "status", new Class[]{}), // 状态
    POWER_STATUS("PowerStatus", "power_status", new Class[]{}), // 电源状态
    ALARM_STATUS("AlarmStatus", "alarm_status", new Class[]{}), // 报警状态

    // 控制参数
    COMMANDS("Commands", "commands", new Class[]{}), // 命令集
    CONTROL("Control", "control", new Class[]{}), // 控制
    DISPATCH_COMMAND("DispatchCommand", "dispatch_command", new Class[]{}), // 发送命令
    MODE("MODE", "mode", new Class[]{}), // 运行模式
    ALARM_LIMIT("AlarmLimit", "alarm_limit", new Class[]{}), // 报警限值
    // 距离参数
    DISTANCE("Distance", "distance", new Class[]{}), // 距离


    Choose("Choose", "choose", new Class[]{}), // 提示用户选择
    Unknown("Unknown", "unknown", new Class[]{}); // 未知

    
    private final String name;   // 参数名称，英文，
    private final String displayName; // 支持markdown标记如上下标
    private final Class<? extends UnitInfo>[] unitClasses;
    
    AttributeClass(String name, String displayName, Class<? extends UnitInfo>[] unitClasses) {
        this.name = name;
        this.displayName = displayName;
        this.unitClasses = unitClasses;
    }
    public String getName() {
        return name;
    }
    public String getDisplayName() {
        // 特殊处理空气质量参数，它们包含HTML标签
        switch (this) {
            case PM2_5:
                return "PM<sub>2.5</sub>";
            case PM10:
                return "PM<sub>10</sub>";
            case SO2:
                return "SO<sub>2</sub>";
            case NOX:
                return "NO<sub>x</sub>";
            case NO2:
                return "NO<sub>2</sub>";
            case O3:
                return "O<sub>3</sub>";
            case O2:
                return "O<sub>2</sub>";
            default:
                return I18nHelper.t("state.class." + displayName);
        }
    }
    
    public Class<? extends UnitInfo>[] getUnitClasses() {
        return unitClasses;
    }
    
    public boolean isValidUnit(UnitInfo unit) {
        for (Class<? extends UnitInfo> unitClass : unitClasses) {
            try {
                UnitInfo[] units = unitClass.getEnumConstants();
                for (UnitInfo u : units) {
                    if (u.getName().equals(unit.getName())) {
                        return true;
                    }
                }
            }
            catch (Exception e) {
                return false;
            }
        }
        return false;
    }
}
