/**
 * 该类负责创建 socket 连接
 */


export class UseWebSocket {
    // 该类socket地址
    private uri: string | undefined
    // 该类实例
    private static useWebSocket: UseWebSocket | undefined

    // websocket 实例
    private socket: WebSocket | undefined
    // 为true时，是心跳重连的websocket断开连接
    private lockReconnect = true

    // 心跳定时器
    private interval: any
    // 心跳间隔时间
    private longStartInterval = 30000

    // 消息超时延时
    private timeout: any
    // 消息超时延时时间
    private longStartTimeout = 5000

    // 用于传递消息
    private socketMsgMap: Map<string, Function> = new Map<string, Function>()

    public constructor (uri: string) {
        this.uri = uri
    }

    /**
     * 向该连接发送消息
     */
    public send (
        body: any
    ) {
        this.getSocketReadyState() === 1 && this.socket!.send(JSON.stringify(body))
    }

    /**
     * 监听该连接返回消息
     */
    public on (key: string, fun: (event: any) => void) {
        if (this.socketMsgMap.has(key)) return
        this.socketMsgMap.set(key, fun)
    }

    /**
     * 移除监听该连接返回消息
     */
    public removeOn (key: string) {
        this.socketMsgMap.delete(key)
    }

    /**
     * 主动关闭连接
     */
    public dispose () {
        // 心跳重连机制设置为 false
        this.lockReconnect = false
        this.socket && this.socket.close() // 离开路由之后断开websocket连接
        this.timeout && clearInterval(this.timeout)
        this.interval && clearTimeout(this.interval)
    }

    /**
     * 连接socket
     */
    public connectionSocket () {
        if (!this.socket && this.uri) {
            this.socket = new WebSocket(this.uri)
            // 建立监听事件
            this.creatListener()
        }
    }

    /**
     * 获取webSocket连接状态
     *     CONNECTING：值为0，表示正在连接。
     *     OPEN：值为1，表示连接成功，可以通信了。
     *     CLOSING：值为2，表示连接正在关闭。
     *     CLOSED：值为3，表示连接已经关闭，或者打开连接失败。
     */
    public getSocketReadyState () {
        if (this.socket) {
            return this.socket.readyState
        }
        return 3
    }

    /**
     * 获取消息传递方法
     */
    public getSocketMsgFun (key: string) {
        const fun = key ? this.socketMsgMap.get(key) : this.socketMsgMap
        return fun
    }

    /**
     * 创建监听事件
     */
    private creatListener () {
        if (!this.socket) return
        this.socket.addEventListener('open', () => {
            console.log('socket连接成功！', this.socket)
            // 成功建立连接后，创建心跳检测
            this.longstart()
        })
        this.socket.addEventListener('message', (event: any) => {
            // 重置心跳
            this.longstart()
            let data = event.data
            try {
                data = JSON.parse(event.data)
            } catch (e) {
                // 返回的数据不是JSON类型
                console.log('收到data:', data)
                return
            }
            // 处理心跳消息 判断当前消息是否为心跳消息，是的话无需处理
            // if (data.header.messageType && data.header.messageType === IMessageType.heartbeat) return

            // 转发正常消息
            // const fun1 = this.socketMsgMap.get('socket:messagePage1')
            // fun1 && typeof fun1 === 'function' && fun1(data)
            // const fun2 = this.socketMsgMap.get('socket:messagePage2')
            // fun2 && typeof fun2 === 'function' && fun2(data)
            for (const [key, value] of this.socketMsgMap) {
                value && typeof value === 'function' && value(data)
            }
        })
        this.socket.addEventListener('error', (event: any) => {
            console.error(`Error from server: ${event}`)
        })
        this.socket.addEventListener('close', () => {
            this.socket = undefined
            if (this.lockReconnect) {
                this.uri && this.connectionSocket()
            }
        })
    }

    /**
     * 创建心跳检测
     */
    private longstart () {
        // 通过 关闭定时器 和 倒计时 进行重置心跳
        this.interval && clearInterval(this.interval)
        this.timeout && clearTimeout(this.timeout)
        // 每隔30s向后端发送一条数据需要跟后端协议发什么内容('ping' => 'pong')
        this.interval = setInterval(() => {
            // 重置监测心跳
            this.send({
                data: 'ping'
            })
            // 发送数据 5s后没有接收到返回的数据进行关闭websocket重连
            this.timeout = setTimeout(() => {
                // 心跳重连机制设置为 true
                this.lockReconnect = true
                // 此时不要清除 interval 定时器，持续发送心跳用于重连
                this.socket && this.socket.close()
            }, this.longStartTimeout)
        }, this.longStartInterval)
    }
}
