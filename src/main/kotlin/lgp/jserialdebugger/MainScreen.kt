package lgp.jserialdebugger

import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import tornadofx.*
import kotlin.concurrent.thread
import gnu.io.CommPortIdentifier
import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.control.TextArea
import javafx.scene.paint.Color
import javafx.scene.text.Font
import javafx.stage.FileChooser
import java.util.Enumeration;

class MainScreen : View("GP串口调试助手") {
    val controller: SerialPortController by inject()

    companion object {
        val selectedPort = SimpleStringProperty()
        val selectedBaudRate = SimpleStringProperty()
        val selectedDataBits = SimpleStringProperty()
        val selectedStopBits = SimpleStringProperty()
        val selectedParityBits = SimpleStringProperty()
        @Suppress("UNCHECKED_CAST")
        val portList: Enumeration<CommPortIdentifier> = CommPortIdentifier.getPortIdentifiers() as Enumeration<CommPortIdentifier>
        var dataSendArea : TextArea by singleAssign()
        lateinit var fileSendDirectory : String
        var dataSendNumers = 0
    }

    //使用TornadoFX提供的EventBus方式，需要创建一个FXEvent事件
    object DataUpdateRequest : FXEvent(EventBus.RunOn.BackgroundThread)
    object DataSendNumbers : FXEvent(EventBus.RunOn.BackgroundThread)

    override val root = gridpane {}

    init {
        with(root) {
            // row1
            row {
                // 数据接收
                titledpane("数据接收") {
                    val hexDisplay = SimpleBooleanProperty()
                    isCollapsible = false
                    vbox {
                        val recvTextArea = textarea {
                            contextmenu {
                                item("另存为").action {
                                    val dataToSave = text;
                                    val fileChooser = FileChooser()
                                    fileChooser.title = "保存文件"
                                    fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("纯文本", "*.txt"))
                                    val file = fileChooser.showSaveDialog(currentStage)
                                    controller.saveToFile(file, dataToSave)
                                }
                            }
                            // TODO: 这里的Font考虑在CSS中实现？
                            font = Font.font("Consolas")

                            // TODO: 变量命名需要优化
                            var temp = ArrayList<Byte>()
                            //isWrapText = true
                            isEditable = false
                            setPrefSize(800.0, 494.4)

                            subscribe<DataUpdateEvent> { event ->
                                if (hexDisplay.value) {
                                    // 当一次只传送一个字符的时候，也可以使用这种方法
                                    // appendText(String.format("%02X", event.data.toByte()) + " ")
                                    appendText(Integer.toHexString(event.data).toUpperCase() + " ")
                                } else {
                                    temp.add(event.data.toByte())
                                    if (temp.size == 2) {
                                        val temp2 = temp.toByteArray()
                                        val temp3 = temp2.toString(charset("GBK"))
                                        appendText(temp3)
                                        temp.clear()
                                    }
                                }
                            }
                        }

                        hbox {
                            checkbox("十六进制显示", hexDisplay) {}
                            checkbox("显示时间")
                            button("清空接收区") {
                                setOnAction {
                                    recvTextArea.clear()
                                }
                            }
                        }
                    }
                }

                // 串口参数
                titledpane("串口参数") {
                    isCollapsible = false
                    vbox {

                        var portNameList: MutableList<String> = mutableListOf()

                        // 将可用串口名添加到List并返回该List
                        while (portList.hasMoreElements()) {
                            val portName: String = portList.nextElement().name;
                            portNameList.add(portName)
                        }

                        hbox {
                            label("串口号")
                            combobox(selectedPort, portNameList) {
                                selectionModel.select(1)
                                setOnAction {
                                    println("value:" + selectedPort.value)
                                }

                            }
                        }

                        hbox {
                            label("波特率")
                            combobox(selectedBaudRate, listOf("100", "300", "1200", "2400", "4800",
                                                              "9600", "14400", "19200", "38400", "56000",
                                                              "57600", "115200", "128000", "256000"))
                            {
                                isEditable = true
                                selectionModel.select("9600")
                                selectedBaudRate.onChange {
                                    // 若检测到数据改变，则更改串口设置
                                    // 因为串口波特率不能为0，此处要检查一下
                                    if(selectedBaudRate.value != "" && selectedBaudRate.value.toInt() > 0) {
                                        modifyPortParams()
                                    }
                                    else{
                                        // TODO: 这里需要提示用户波特率不能为空，然后设置为默认波特率9600
                                        selectedBaudRate.set("9600")
                                        selectionModel.select("9600")
                                    }
                                }
                            }
                        }

                        hbox {
                            label("数据位")
                            combobox(selectedDataBits, listOf("8", "7", "6", "5")) {
                                selectionModel.select("8")
                                selectedDataBits.onChange {
                                    // 若检测到数据改变，则更改串口设置
                                    modifyPortParams()
                                }
                            }
                        }

                        hbox {
                            label("停止位")
                            combobox(selectedStopBits, listOf("1", "2")) {
                                selectionModel.select("1")
                                selectedStopBits.onChange {
                                    // 若检测到数据改变，则更改串口设置
                                    modifyPortParams()
                                }
                            }
                        }

                        hbox {
                            label("校验位")
                            combobox(selectedParityBits, listOf("NONE", "ODD", "EVEN")) {
                                selectionModel.select("NONE")
                                selectedParityBits.onChange {
                                    // 若检测到数据改变，则更改串口设置
                                    modifyPortParams()
                                }
                            }
                        }

                        hbox {
                            textfield("未打开")

                            button("打开串口") {
                                action {
                                    //串口号 波特率 数据位 停止位 校验位
                                    controller.openPort(selectedPort.value, selectedBaudRate.value, selectedDataBits.value, selectedStopBits.value, selectedParityBits.value)
                                    fire(DataUpdateRequest)
                                }

                                style {
                                    baseColor = Color.BLUE
                                }
                            }

                            button("关闭串口") {
                                action {
                                    controller.closePort()
                                }
                            }
                        }
                    }
                }
            }

            // row2
            row {
                // 文件发送
                titledpane("文件发送") {
                    isCollapsible = false
                    vbox {
                        hbox {
                            label("文件路径")
                            val fileDirectoryInput = textfield {

                            }
                            button("选择文件") {
                                action {
                                    // TODO: fileSendDirectory可以直接换为File类型吗
                                    fileSendDirectory = chooseFile("选择文件", arrayOf(FileChooser.ExtensionFilter("纯文本文件","*.txt"))).toString().replace("[","").replace("]","")
                                    fileDirectoryInput.text = fileSendDirectory
                                }
                            }
                            button("发送文件") {
                                action {
                                    // TODO:此处应该检查文件的类型，是否是纯文本文件。不然发送文件的函数在解析文件的时候可能出错误？
                                    controller.sendFile(fileDirectoryInput.text)
                                }
                            }
                        }

                        hbox {
                            label("发送进度")
                            progressbar {
                                thread {
                                    for (i in 1..100) {
                                        Platform.runLater { progress = i.toDouble() / 100.0 }
                                        Thread.sleep(100)
                                    }
                                }
                            }
                            button("取消发送")
                        }
                    }
                }

                //数据发送
                titledpane("数据发送") {
                    var hexSend = SimpleBooleanProperty()
                    isCollapsible = false
                    vbox {
                        hbox {
                            dataSendArea = textarea {
                                font = Font.font("Consolas")
                                setPrefSize(300.0, 200.0)
                            }
                            button("发送") {
                                useMaxSize = true
                                action {
                                    controller.sendData(dataSendArea.text, hexSend.value)
                                }
                            }
                        }

                        hbox {
                            label("自动发送周期：")
                            val autoSendCycle = textfield("1000") { }
                            text("毫秒")
                            // TODO:处于自动发送状态时，按钮变为“取消自动发送”
                            button("自动发送") {
                                setOnAction {
                                    autoSendCycle.isEditable = false
                                    controller.autoSendData(dataSendArea.text, hexSend.value, autoSendCycle.text.toInt())
                                }
                            }
                        }

                        hbox {
                            checkbox("十六进制发送", hexSend) {}
                        }

                        hbox {
                            label("已发送")
                            textfield() {
                                subscribe<DataSendNumbersEvent> { event ->
                                    text = (++dataSendNumers).toString()
                                }
                            }
                        }

                        hbox {
                            button("计数清零") { }
                        }
                    }
                }
            }
        }
    }

    private fun modifyPortParams() {
        controller.modifyPortParameters(selectedPort.value, selectedBaudRate.value, selectedDataBits.value, selectedStopBits.value, selectedParityBits.value)
    }

}