package oam.security.model.resource.security;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CopyFileCommandLineRunner implements CommandLineRunner{
	
	String sourceAnsFolder = "ans/";
	String copyFolderPath = "/uploadPict";
	String needFileNames[] = {"Account.jpg", "Chest X-Ray.jpg", "missile.jpg", 
			  "Panasonic.jpg", "Taiwan.jpg"};

	@Override
	public void run(String... args) throws Exception {
		Path sourceFolder = Paths.get(sourceAnsFolder);
		Path targetFolder = Paths.get(copyFolderPath);
		log.info("sourceFolder:" + sourceFolder.toString());
		log.info("targetFolder:" + targetFolder.toString());
		showNeedFile();
		
		try {
            // 使用 Files.walkFileTree 遞迴地複製指定副檔名的檔案
            Files.walkFileTree(sourceFolder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (checkFileIsNeed(file.toString())) {
                        Path targetFile = targetFolder.resolve(sourceFolder.relativize(file));
                        Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path targetDir = targetFolder.resolve(sourceFolder.relativize(dir));
                    Files.createDirectories(targetDir);
                    return FileVisitResult.CONTINUE;
                }
            });

            System.out.println("指定副檔名的檔案複製完成！");
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
	
	public boolean checkFileIsNeed(String fileName) {
		for(int i = 0; i < needFileNames.length; i++) {
			if(fileName.contains(needFileNames[i])) {
				return true;
			}
		}
		return false;
	}
	
	public void showNeedFile() {
		log.info("The need files are");
		for(int i = 0; i < needFileNames.length; i++) {
			log.info(needFileNames[i]);
		}
	}
}
