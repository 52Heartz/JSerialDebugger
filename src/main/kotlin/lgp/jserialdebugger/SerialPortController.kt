package lgp.jserialdebugger

import gnu.io.*
import tornadofx.*
import java.io.*
import kotlin.concurrent.thread

class SerialPortController : Controller(), SerialPortEventListener {

    companion object {
        lateinit var serialPortIdentifier: CommPortIdentifier
        lateinit var serialPort: CommPort
        lateinit var outputStream: OutputStream
        lateinit var inputStream: InputStream
        var parityBits = 0
        var autoSendDataSwitch : Boolean = false
    }

    /**
     * 打开串口
     * @param portIdentifier 串口名称
     * @param baudRate 波特率
     * @param dataBits 数据位
     * @param stopBits 停止位
     * @param parityBitsParam 校验位，起这个形参名是因为要从“NONE”、“ODD”String类型转为“0”、“1”等数字类型
     */
    fun openPort(portIdentifier : String, baudRate : String, dataBits : String, stopBits : String, parityBitsParam : String ){
        serialPortIdentifier = CommPortIdentifier.getPortIdentifier(portIdentifier) //串口号
        serialPort = serialPortIdentifier.open("GPSerialPortDebugger", 5000) as SerialPort //使用者  和 最大响应时长(ms)

        // 校验位参数parityBitsParam为“NONE”，“ODD”等字符串，在这里转换为与setSerialPortParams方法的参数相对应Int类型
        when(parityBitsParam) {
            "NONE" -> parityBits = 0
            "ODD" -> parityBits = 1
            "EVEN" -> parityBits = 2
        }

        subscribe<MainScreen.DataUpdateRequest> {}
        subscribe<MainScreen.DataSendNumbers> {}

        try {
            //波特率 数据位 停止位 校验位
            (serialPort as SerialPort).setSerialPortParams(baudRate.toInt(), dataBits.toInt(), stopBits.toInt(), parityBits)
            (serialPort as SerialPort).flowControlMode = SerialPort.FLOWCONTROL_NONE

            inputStream = serialPort.inputStream
            outputStream = serialPort.outputStream

            (serialPort as SerialPort).addEventListener(this)
            (serialPort as SerialPort).notifyOnDataAvailable(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 在串口打开的情况下，修改串口参数
     * 参数和打开串口函数的参数相同
     * @param portIdentifier 串口名称
     * @param baudRate 波特率
     * @param dataBits 数据位
     * @param stopBits 停止位
     * @param parityBitsParam 校验位，起这个形参名是因为要从“NONE”、“ODD”String类型转为“0”、“1”等数字类型
     */
    fun modifyPortParameters(portIdentifier : String, baudRate : String, dataBits : String, stopBits : String, parityBitsParam : String ){
        when(parityBitsParam) {
            "NONE" -> parityBits = 0
            "ODD" -> parityBits = 1
            "EVEN" -> parityBits = 2
        }

        try {
            //波特率 数据位 停止位 校验位
            (serialPort as SerialPort).setSerialPortParams(baudRate.toInt(), dataBits.toInt(), stopBits.toInt(), parityBits)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 关闭串口
     */
    fun closePort(){
        serialPort.close()
        inputStream.close()
        outputStream.close()
    }

    /**
     * 重写的serialEvent函数，如果有串口事件，在这里处理
     */
    override fun serialEvent(event : SerialPortEvent?) {
        try {
            if (event != null) {
                when (event.eventType) {
                // 10 通讯中断
                    SerialPortEvent.BI -> println("与串口设备通讯中断")

                // 7 溢位（溢出）错误
                    SerialPortEvent.OE -> println("7 溢位（溢出）错误")

                // 9 帧错误
                    SerialPortEvent.FE -> println("9 帧错误")

                // 8 奇偶校验错误
                    SerialPortEvent.PE -> println("8 奇偶校验错误")

                // 6 载波检测
                    SerialPortEvent.CD -> println("6 载波检测")

                // 3 清除待发送数据
                    SerialPortEvent.CTS -> println("3 清除待发送数据")

                // 4 待发送数据准备好了
                    SerialPortEvent.DSR -> println("4 待发送数据准备好了")

                // 5 振铃指示
                    SerialPortEvent.RI -> println("5 振铃指示")

                // 2 输出缓冲区已清空
                    SerialPortEvent.OUTPUT_BUFFER_EMPTY -> println("2 输出缓冲区已清空")

                // 1 串口存在可用数据
                    // TODO: 这里的逻辑可能有些问题，也许可以优化
                    SerialPortEvent.DATA_AVAILABLE -> {
                        var bufferLength = inputStream.available()
                        while (bufferLength != 0) {
                            var data = inputStream.read()
                            fire(DataUpdateEvent(data))
                            bufferLength = inputStream.available()
                        }
                    }
                }
            }
        } catch (e : Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 发送数据
     * @param data 要发送的数据
     * @param hexSend 是否开启十六进制发送
     * TODO: 这个地方要支持多种编码格式，比如UTF-8等，以后加上去，并添加到设置选项中
     */
    fun sendData(data : String, hexSend : Boolean = false) {
        val encodedData = encodeData(data)
        if (hexSend) {
            for (i in encodedData){
                outputStream.write(i.toInt())
                fire(DataSendNumbersEvent(1))
            }
        }
        else {
            outputStream.write(encodedData)
        }
    }

    /**
     * 自动发送数据
     * @param data 要发送的数据
     * @param hexSend 是否开启十六进制发送
     * @param autoSendCycle 自动发送周期
     * TODO: 这个地方要支持多种编码格式，比如UTF-8等，以后加上去，并添加到设置选项中
     */
    fun autoSendData(data : String, hexSend : Boolean = false, autoSendCycle : Int) {
        autoSendDataSwitch = true
        val encodedData = encodeData(data)

        // TODO:这里需要监听串口的状态，如果串口关闭了，就停止线程
        thread(start = true, name = "autoSendDataThread"){
            while(autoSendDataSwitch){
                if (hexSend) {
                    for (i in encodedData){
                        outputStream.write(i.toInt())
                        fire(DataSendNumbersEvent(1))
                    }
                }
                else {
                    outputStream.write(encodedData)
                }
                Thread.sleep(autoSendCycle.toLong())
            }
        }
    }

    /**
     * 发送文件
     * @param fileDirectory 要发送的文件的文件路径
     */
    fun sendFile(fileDirectory : String) {
        thread(start = true, isDaemon = false, name = "fileSendThread") {
            // TODO:如果不是纯文本文件，readText()函数是否会抛出异常？
            val encodedData = File(fileDirectory).readText(charset("GBK")).toByteArray(charset("GBK"))
            for (i in encodedData){
                outputStream.write(i.toInt())
                fire(DataSendNumbersEvent(1))
            }
        }
    }

    /**
     * 保存文件
     * @param file 要保存的文件，File类型
     * @param data 要向文件中写入的数据,
     * 向文件写入数据是重新写入方式，不是追加方式
     */
    fun saveToFile(file : File, data : String) {
        try {
            file.printWriter().use { out ->
                out.print(data)
            }
        } catch ( e : Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 以指定字符集编码字符串，返回值为ByteArray类型
     * @param data 要编码的数据
     * @param encoding 指定字符集的名称
     */
    fun encodeData(data : String, encoding : String = "GBK") : ByteArray {
        return data.replace("\n", "\r\n").toByteArray(charset(encoding))
    }

}

/**
 * DataUpdateEvent、DataSendNumbersEvent都是用于前后段数据传递的事件类。更多信息可参考TornadoFX的EventBus
 */
class DataUpdateEvent(val data : Int) : FXEvent()

class DataSendNumbersEvent(val data : Int) : FXEvent()