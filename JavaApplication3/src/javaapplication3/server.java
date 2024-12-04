
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class server {
    private static final int PORT = 5555;
    private static final int COMMENT_PORT = 5556; // Port cho bình luận
    private static List<Socket> clientSockets = new ArrayList<>(); // Kết nối hình ảnh
    private static List<Socket> commentSockets = new ArrayList<>(); // Kết nối bình luận
    private static JLabel imageLabel;
    private static JTextArea commentArea;
    private static volatile boolean capturing = false;
    private static volatile boolean paused = false;
    private static Thread captureThread;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Screen Share Server");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        imageLabel = new JLabel("Nhấn 'Start Capture' để bắt đầu", JLabel.CENTER);
        frame.add(imageLabel, BorderLayout.CENTER);

        JButton toggleButton = new JButton("Start Capture");
        toggleButton.addActionListener(e -> toggleCapture(toggleButton));
        frame.add(toggleButton, BorderLayout.SOUTH);

        // JTextArea để hiển thị bình luận
        commentArea = new JTextArea(5, 20);
        commentArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(commentArea);
        frame.add(scrollPane, BorderLayout.NORTH);

        frame.setVisible(true);

        // Bắt đầu lắng nghe client
        startListening();
    }

    private static void toggleCapture(JButton toggleButton) {
        if (capturing) {
            paused = !paused;
            toggleButton.setText(paused ? "Resume Capture" : "Stop Capture");
        } else {
            capturing = true;
            paused = false;
            toggleButton.setText("Stop Capture");
            startCapture();
        }
    }

    private static void startCapture() {
        captureThread = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                imageLabel.setText("Server started, waiting for connection...");

                Robot robot = new Robot();
                Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());

                while (capturing) {
                    BufferedImage screenImage = robot.createScreenCapture(screenRect);
                    BufferedImage resizedImage = resizeImage(screenImage, 800, 600);

                    SwingUtilities.invokeLater(() -> {
                        imageLabel.setIcon(new ImageIcon(resizedImage));
                    });

                    // Kiểm tra và chấp nhận kết nối mới từ client
                    serverSocket.setSoTimeout(50);
                    try {
                        Socket clientSocket = serverSocket.accept();
                        clientSockets.add(clientSocket);
                        imageLabel.setText("Client connected. Total clients: " + clientSockets.size());
                    } catch (SocketTimeoutException e) {
                        // Không làm gì, tiếp tục xử lý các client đã kết nối
                    }

                    // Gửi dữ liệu màn hình tới tất cả các client
                    for (int i = 0; i < clientSockets.size(); i++) {
                        Socket clientSocket = clientSockets.get(i);
                        try {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(screenImage, "png", baos);
                            byte[] imageBytes = baos.toByteArray();

                            DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
                            dos.writeInt(imageBytes.length);
                            dos.write(imageBytes);
                            dos.flush();
                        } catch (IOException e) {
                            // Nếu client ngắt kết nối, xóa khỏi danh sách
                            try {
                                clientSocket.close();
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }
                            clientSockets.remove(i);
                            i--;
                            System.out.println("Client disconnected. Total clients: " + clientSockets.size());
                        }
                    }

                    Thread.sleep(5);
                }

                for (Socket clientSocket : clientSockets) {
                    clientSocket.close();
                }
                clientSockets.clear();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        captureThread.start();
    }

    private static BufferedImage resizeImage(BufferedImage originalImage, int width, int height) {
        Image tmp = originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();
        return resizedImage;
    }

    private static void startListening() {
        new Thread(() -> {
            try (ServerSocket commentServerSocket = new ServerSocket(COMMENT_PORT)) { 
                while (true) {
                    Socket clientSocket = commentServerSocket.accept();
                    commentSockets.add(clientSocket);
                    new Thread(() -> {
                        try {
                            DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
                            while (true) {
                                String comment = dis.readUTF();
                                broadcastComment(comment);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void broadcastComment(String comment) {
        SwingUtilities.invokeLater(() -> {
            commentArea.append("Client: " + comment + "\n");
        });
        for (Socket clientSocket : commentSockets) {
            try {
                DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
                dos.writeUTF("Server: " + comment);
                dos.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}