package by.mrj;

import by.mrj.message.domain.Message;
import by.mrj.message.domain.Registration;
import by.mrj.message.types.Command;
import by.mrj.message.util.MessageUtils;
import by.mrj.message.util.NetUtils;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Simplest possible implementation of "DNS seed".
 * NOTE: actually not a DNS seed :)
 * Returns peers addresses including requester address.
 */
@Log4j2
public class SimpleSeed {

    static {
        if (Boolean.valueOf(System.getProperty("tor.enabled"))) {
            String torHost = System.getProperty("tor.host");
            String torPort = System.getProperty("tor.port");
            if (torHost == null || torPort == null) {
                throw new RuntimeException(
                        String.format("Wrong setup. Tor Host: [%s], Tor Port: [%s]", torHost, torPort));
            }

            System.setProperty("socksProxyHost", torHost);
            System.setProperty("socksProxyPort", torPort);
        }
    }

    public static void main(String[] args) {
        Set<String> addresses = new HashSet<>();
        try (ServerSocket ss = new ServerSocket(8353)) {
            while (true) {
                // receive request
                log.info("Listening on default port...");
                Socket socket = ss.accept();

                log.info("Accepted request");

                CompletableFuture.runAsync(() -> {
                    try (Socket sockX = socket) {
                        @SuppressWarnings("unchecked")
                        Message<Registration> message = (Message<Registration>) NetUtils.deserialize(sockX.getInputStream());

                        processMessage(message);

                        String address = message.getPayload().getNetworkAddress();
                        log.info("Got peer network address [{}]", address);

                        if (address == null || address.trim().length() < 7) // todo: weird check...
                            return;

                        addresses.add(address);
                        String peers = addresses.stream().collect(Collectors.joining(";"));

                        Message<Registration> registrationMessage = MessageUtils.makeMessageWithSig(
                                Registration.builder().networkAddress(peers).build(),
                                Command.REGISTRATION);

                        byte[] bytes = NetUtils.serialize(registrationMessage);
                        OutputStream os = sockX.getOutputStream();
                        os.write(bytes);
                        os.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }).exceptionally(throwable -> {
                    throw new RuntimeException(throwable);
                });
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
