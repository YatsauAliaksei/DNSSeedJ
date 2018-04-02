package by.mrj;

import by.mrj.message.domain.Message;
import by.mrj.message.domain.Registration;
import by.mrj.message.types.Command;
import by.mrj.message.util.MessageUtils;
import by.mrj.message.util.NetUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

/**
 * Simplest possible implementation of "DNS seed".
 * NOTE: actually not a DNS seed :)
 */
public class SimpleSeed {

    public static void main(String[] args) {
        Set<String> addresses = new HashSet<>();
        try (ServerSocket ss = new ServerSocket(53)) {
            while (true) {
                // receive request
                Socket socket = ss.accept();
                @SuppressWarnings("unchecked")
                Message<Registration> message = (Message<Registration>) NetUtils.deserialize(socket.getInputStream());

                processMessage(message);

                String address = message.getAddress();
                addresses.add(address);

                OutputStream os = socket.getOutputStream();

                String peers = addresses.stream().reduce((s, s2) -> s + ";" + s2).orElse("");

                Message<Registration> registrationMessage = MessageUtils.makeMessageWithPubKey(
                        Registration.builder().address(peers).build(),
                        Command.REGISTRATION);

                byte[] bytes = NetUtils.serialize(registrationMessage);
                os.write(bytes);
                os.flush();
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void processMessage(Message<?> message) {
        if (!MessageUtils.verifyMessage(message)) {
            throw new RuntimeException("Message verification failed");
        }
    }
}
