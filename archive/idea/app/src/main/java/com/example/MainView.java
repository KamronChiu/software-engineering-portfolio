package com.example;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import java.io.IOException;

@Route("")
@PageTitle("Hello, Vaadin!")
public class MainView extends VerticalLayout {
    public MainView() {
        add(new H1("Hello, Vaadin!"));
        var nameField = new TextField("What is your name?");
        add(nameField);
        var greeting = new Paragraph();
        greeting.setVisible(false);
        add(greeting);
        add(new Button("Say Hello", event -> {
            greeting.setText("Hello, %s!".formatted(nameField.getValue()));
            greeting.setVisible(!nameField.isEmpty());
        }));
        var qrImage = new Image();
        add(new Button("Generate QR Code", event -> {
            if (nameField.isEmpty()) {
                nameField.setErrorMessage("Enter a name");
                nameField.setInvalid(true);
            } else {
                nameField.setInvalid(false);
                qrImage.setSrc(generateQrCode(nameField.getValue()));
            }
        }));
        add(qrImage);
    }

    private DownloadHandler generateQrCode(String text) {
        return downloadEvent -> {
            try (var out = downloadEvent.getOutputStream()) {
                var qrWriter = new QRCodeWriter();
                var bitMatrix = qrWriter.encode(text, BarcodeFormat.QR_CODE, 300, 300);
                downloadEvent.setContentType("image/png");
                MatrixToImageWriter.writeToStream(bitMatrix, "PNG", out);
            } catch (WriterException e) {
                throw new IOException(e);
            }
        };
    }
}