package com.focusflow.app;

import com.focusflow.ui.components.AppIcon;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Build helper: writes a 256px PNG-in-ICO file for jpackage. Usage: IconExporter out.ico */
public final class IconExporter {
    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "focusflow.ico");
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(AppIcon.create(256), "png", png);
        byte[] data = png.toByteArray();
        ByteBuffer b = ByteBuffer.allocate(22 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0).putShort((short) 1).putShort((short) 1);
        b.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
        b.putShort((short) 1).putShort((short) 32).putInt(data.length).putInt(22);
        b.put(data);
        Files.write(out, b.array());
        System.out.println("Wrote " + out);
    }
}
