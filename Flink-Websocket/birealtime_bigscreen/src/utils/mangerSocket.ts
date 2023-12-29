import { UseWebSocket } from './useWebSocket'

/**
 * 该类负责管理 socket 连接
 */
class MangerSocket {
    private static mangerSocket: MangerSocket | undefined
    public dataSocket: UseWebSocket | undefined
    public warningSocket: UseWebSocket | undefined

    private constructor () {
        //配置 dataSocket 地址
        this.dataSocket = new UseWebSocket('ws://127.0.0.1:5656/topic/live-msg')
        // 配置warningSocket 地址
        this.warningSocket = new UseWebSocket('ws://10.111.182.120:8070')
        // 这里是我打印用于查看webscoket状态的，所以注释掉了
        // setInterval(() => {
        //   console.log('this.warningSocket：',this.warningSocket)
        //   this.warningSocket && console.log('::', this.warningSocket.getSocketReadyState())
        // }, 3000)
    }

    /**
     * 获取该类实例
     */
    static instance (): MangerSocket {
        if (!this.mangerSocket) {
            this.mangerSocket = new MangerSocket()
        }
        return this.mangerSocket
    }

    /**
     * 主动关闭dataSocket连接
     */
    public disposeData () {
        this.dataSocket && this.dataSocket.dispose()
    }

    /**
     * 主动关闭warningSocket连接
     */
    public disposeWarning () {
        this.warningSocket && this.warningSocket.dispose()
    }
}
export default MangerSocket.instance()
