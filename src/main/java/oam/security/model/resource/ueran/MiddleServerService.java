package oam.security.model.resource.ueran;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import oam.security.service.JSchService;

@Service
@Slf4j
public class MiddleServerService {
	
	@Autowired
	public JSchService jschService;

}
