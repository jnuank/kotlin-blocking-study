import socket
import select

# socket 作成 → bind → listen
server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server.bind(('0.0.0.0', 8080))
server.listen(128)
server.setblocking(False)  # O_NONBLOCK: ブロックしない

print(f"[起動] server fd={server.fileno()}, port=8080")

# epoll 作成・server_fd を登録
epoll = select.epoll()
epoll.register(server.fileno(), select.EPOLLIN)
print(f"[epoll] server_fd を epoll に登録")

connections = {}

try:
    while True:
        print("\n[待機] epoll_wait 呼び出し... (OSに委譲中)")
        events = epoll.poll()  # ← ここで OS が監視。イベントが来るまで寝る
        print(f"[起床] {len(events)}個のイベント発生!")

        for fd, event in events:
            if fd == server.fileno():
                # 新規接続
                conn, addr = server.accept()
                conn.setblocking(False)
                epoll.register(conn.fileno(), select.EPOLLIN)
                connections[conn.fileno()] = conn
                print(f"  [接続] {addr} → fd={conn.fileno()}")

            elif event & select.EPOLLIN:
                # データ受信
                conn = connections[fd]
                data = conn.recv(1024)
                if data:
                    first_line = data.decode(errors='replace').split('\n')[0]
                    print(f"  [受信] fd={fd}: {first_line}")
                    response = b"HTTP/1.1 200 OK\r\nContent-Length: 13\r\n\r\nHello, epoll!"
                    conn.send(response)
                # 接続を閉じて epoll から削除
                epoll.unregister(fd)
                conn.close()
                del connections[fd]
                print(f"  [切断] fd={fd}")

finally:
    epoll.close()
    server.close()
