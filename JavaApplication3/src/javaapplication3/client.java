import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;

public class client {
    private static final String SERVER_IP = "172.20.10.7"; // Địa chỉ IP server
    private static final int PORT = 5555; // Cổng nhận hình ảnh
    private static final int COMMENT_PORT = 5556; // Cổng gửi bình luận
    private static Socket socket;
    private static Socket commentSocket;
    private static DataOutputStream commentOut;

    public static void main(String[] args) {
        JFrame frame = new JFrame("Screen Share Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());
        frame.setVisible(true);

        try {
            socket = new Socket(SERVER_IP, PORT); // Kết nối đến server nhận hình ảnh
            commentSocket = new Socket(SERVER_IP, COMMENT_PORT); // Kết nối đến server gửi bình luận
            commentOut = new DataOutputStream(commentSocket.getOutputStream()); // Mở luồng gửi bình luận

            // Tạo input stream để nhận dữ liệu từ server
            InputStream is = socket.getInputStream();
            DataInputStream dis = new DataInputStream(is);

            // Tạo panel để chứa hình ảnh
            JPanel imagePanel = new JPanel();
            frame.add(imagePanel, BorderLayout.CENTER);

            // Tạo panel nhập bình luận
            JPanel commentPanel = new JPanel();
            JTextField commentField = new JTextField(30);
            JButton sendButton = new JButton("Gửi Bình Luận");

            sendButton.addActionListener(e -> {
                String comment = commentField.getText().trim();
                if (!comment.isEmpty()) {
                    sendComment(comment);
                    commentField.setText(""); // Xóa nội dung sau khi gửi
                }
            });

            commentPanel.add(commentField);
            commentPanel.add(sendButton);
            frame.add(commentPanel, BorderLayout.SOUTH);

            // Nhận và hiển thị hình ảnh từ server
            while (true) {
                // Đọc kích thước ảnh
                int imageSize = dis.readInt();
                byte[] imageBytes = new byte[imageSize];

                // Đọc dữ liệu ảnh
                dis.readFully(imageBytes);

                // Tạo hình ảnh từ mảng byte
                ImageIcon imageIcon = new ImageIcon(imageBytes);
                JLabel imageLabel = new JLabel(imageIcon);

                // Cập nhật giao diện hiển thị hình ảnh
                SwingUtilities.invokeLater(() -> {
                    imagePanel.removeAll(); // Xóa tất cả các thành phần trước đó
                    imagePanel.add(imageLabel);
                    imagePanel.revalidate();
                    imagePanel.repaint();
                });
            }
        } catch (IOException e) {
            System.err.println("IO Exception: " + e.getMessage());
        }
    }

    private static void sendComment(String comment) {
        try {
            if (commentOut != null) {
                commentOut.writeUTF(comment); // Gửi chuỗi bình luận
                commentOut.flush(); // Đảm bảo dữ liệu được gửi ngay lập tức
                System.out.println("Đã gửi bình luận: " + comment);
            } else {
                System.err.println("Luồng gửi bình luận chưa được khởi tạo!");
            }
        } catch (IOException e) {
            System.err.println("Không thể gửi bình luận: " + e.getMessage());
        }
    }
}