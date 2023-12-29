from watchdog.events import *  # 同上
from watchdog.observers import Observer  # WatchDog相关的模块，用来做文件监控
import subprocess
from multiprocessing import Process
import time
import properties
import codecs # Chinese unicode transfer

from kafka import KafkaProducer

prop_path='config.properties'

props = properties.parse(prop_path)
kafka_addr = props.get('kafka_address')
kafaka_topic = props.get('kafka_topic')
monitor_dir = props.get('monitor_dir')

class FileEventHandler(FileSystemEventHandler):
    def __init__(self):
        FileSystemEventHandler.__init__(self)

    def on_moved(self, event):
        pass

    def on_created(self, event):  # 逻辑代码是base在当文件新增的时候进行开发
        if event.is_directory:
            pass
        else:
            print("file created:{0}".format(event.src_path))

    def on_deleted(self, event):
        pass

    def on_modified(self, event):
        if event.is_directory:
            pass
        else:
            if not re.search('.filepart',event.src_path) and  not re.search('backup',event.src_path):
                FILE_MODIFY_PROCESS = Process(target=SEND_TXN_LOG_KAFKA,args=(event.src_path, kafaka_topic))
                FILE_MODIFY_PROCESS.start()




def SEND_TXN_LOG_KAFKA(src_file_path,topic):
    # prop_path='config.properties'
    #
    # props = properties.parse(prop_path)
    # kafka_addr = props.get('kafka_address')

    producer = KafkaProducer(bootstrap_servers=kafka_addr,value_serializer=lambda m: bytes(m, encoding="gb2312"))

    try:
        with codecs.open(src_file_path, 'rb',encoding='gb2312') as f:
            context = f.readlines()
            for eachline in context:
                #print(eachline)
                producer.send(topic, eachline)
        f.close()


    except Exception as e:

        raise e
        Logger("RUN kafka  ERROR....")
        print("RUN kafka  ERROR....")
    finally:
        pass



def FILE_HANDLER_PROCESS(file_path,bool):
    observer = Observer()  # 文件监控开始
    event_handler = FileEventHandler()  # 启动一个线程文件事件
    observer.schedule(event_handler, file_path, bool)  # 对指定的文件夹进行监控，当文件更新后触发一些操作
    #observer.schedule(event_handler,"E:\\APP\\Python_script\\SEMIR_REALTIME_ETL_PROCCESS\\dist\\LOG",True)
    observer.start()  # 文件线程 监控开始s
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        observer.stop()
        time.sleep(1)
    finally:
        observer.start()
    #observer.join()


if __name__ == '__main__':


    log_monitor_process = Process(target=FILE_HANDLER_PROCESS,args=(monitor_dir,True))

    log_monitor_process.start()
    log_monitor_process.join()

