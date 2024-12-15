package com.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.ZipEntry;
import org.objectweb.asm.*;

public class JarObfuscator extends JFrame {

    private JTextArea logArea;
    private JButton obfuscateBtn;
    private JButton loadBtn;
    private JButton saveBtn;
    private File jarFile;
    private File tempDirectory;

    public JarObfuscator() {
        setTitle("Обфускатор JAR файлов");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        logArea = new JTextArea(15, 50);
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        JPanel buttonPanel = new JPanel();
        obfuscateBtn = new JButton("Обфусцировать JAR");
        loadBtn = new JButton("Загрузить JAR");
        saveBtn = new JButton("Сохранить обфусцированный JAR");
        saveBtn.setEnabled(false);

        buttonPanel.add(loadBtn);
        buttonPanel.add(obfuscateBtn);
        buttonPanel.add(saveBtn);

        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        loadBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectJarFile();
            }
        });

        obfuscateBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (jarFile != null) {
                    try {
                        obfuscateJarFile(jarFile);
                    } catch (Exception ex) {
                        logArea.append("Ошибка во время обфускации: " + ex.getMessage() + "\n");
                    }
                } else {
                    logArea.append("Пожалуйста, выберите JAR файл!\n");
                }
            }
        });

        saveBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                saveObfuscatedJarFile();
            }
        });
    }

    private void selectJarFile() {
        JFileChooser fileChooser = new JFileChooser();
        int option = fileChooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            jarFile = fileChooser.getSelectedFile();
            logArea.append("Загруженный: " + jarFile.getName() + "\n");
            saveBtn.setEnabled(false);
        }
    }

    private void obfuscateJarFile(File jar) throws IOException {
        tempDirectory = new File("temp_obfuscated");
        if (!tempDirectory.exists()) {
            tempDirectory.mkdir();
        }

        try (JarFile jarFile = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                File outputFile = new File(tempDirectory, entry.getName());

                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                    continue;
                }

                try (InputStream inputStream = jarFile.getInputStream(entry)) {
                    outputFile.getParentFile().mkdirs();
                    try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, length);
                        }
                    }
                }
            }
        }

        obfuscateClassesInDirectory(tempDirectory);
        logArea.append("Обфусцирование завершено! Теперь вы можете сохранить обфусцированный JAR файл.\n");
        saveBtn.setEnabled(true);
    }

    private void obfuscateClassesInDirectory(File directory) throws IOException {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                obfuscateClassesInDirectory(file);
            } else if (file.getName().endsWith(".class")) {
                logArea.append("Обфусцирование: " + file.getName() + "\n");
                obfuscateClassFile(file);
            }
        }
    }

    private void obfuscateClassFile(File classFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(classFile)) {
            ClassReader classReader = new ClassReader(fis);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);

            classReader.accept(new ClassVisitor(Opcodes.ASM9, classWriter) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    // Генерация нового имени метода
                    String obfuscatedMethodName = "m_" + new Random().nextInt(1000);
                    return super.visitMethod(access, obfuscatedMethodName, descriptor, signature, exceptions);
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    // Генерация нового имени поля
                    String obfuscatedFieldName = "f_" + new Random().nextInt(1000);
                    return super.visitField(access, obfuscatedFieldName, descriptor, signature, value);
                }
            }, 0);

            // Запись обфусцированного класса обратно в файл
            try (FileOutputStream fos = new FileOutputStream(classFile)) {
                fos.write(classWriter.toByteArray());
            }
        }
    }

    private void saveObfuscatedJarFile() {
        JFileChooser fileChooser = new JFileChooser();
        int option = fileChooser.showSaveDialog(this);

        if (option == JFileChooser.APPROVE_OPTION) {
            File outputJarFile = fileChooser.getSelectedFile();

            try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(outputJarFile))) {
                addDirectoryToJar(tempDirectory, tempDirectory, jarOutputStream);
            } catch (IOException e) {
                logArea.append("Ошибка сохранения обфусцированного JAR файла: " + e.getMessage() + "\n");
            }

            logArea.append("Сохранённый обфусцированный JAR файл: " + outputJarFile.getName() + "\n");
        }
    }

    private void addDirectoryToJar(File rootDir, File sourceDir, JarOutputStream jarOutputStream) throws IOException {
        File[] files = sourceDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String entryName = rootDir.toPath().relativize(file.toPath()).toString();
            if (file.isDirectory()) {
                jarOutputStream.putNextEntry(new ZipEntry(entryName + "/"));
                jarOutputStream.closeEntry();
                addDirectoryToJar(rootDir, file, jarOutputStream);
            } else {
                jarOutputStream.putNextEntry(new ZipEntry(entryName));
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) != -1) {
                        jarOutputStream.write(buffer, 0, length);
                    }
                }
                jarOutputStream.closeEntry();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new JarObfuscator().setVisible(true);
            }
        });
    }
}