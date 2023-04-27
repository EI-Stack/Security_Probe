package oam.security.model.resource.postgres;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrafficRecordRepository extends CrudRepository<TrafficRecord, String>{
	@Query(value="SELECT * FROM public.traffic_record WHERE record_hour=?1", nativeQuery = true)
	List<TrafficRecord> findByHour(int hour);
}
