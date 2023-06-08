package org.databiosphere.workspacedataservice.service;

import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.databiosphere.workspacedataservice.shared.model.Record;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Stream;


public class TsvSupport {

	private TsvSupport() {
	}

	public static void writeCsvToStream (Stream<Record> records, OutputStream stream, List<String> headers) throws IOException {

		CsvSchema tsvHeaderSchema = CsvSchema.emptySchema()
		.withEscapeChar('\\')
		.withColumnSeparator('\t')
		.withNullValue("");

		final CsvMapper tsvMapper = CsvMapper.builder()
		.build();

		SequenceWriter seqW = tsvMapper.writer(tsvHeaderSchema)
			.writeValues(stream);
		seqW.write(headers);
		// First header is Primary Key, and value is stored in record.id. Remove header here and add record.id manually.
		headers.remove(0);
		for (Record record : records.toList()) {
			List<String> row = recordToRow(record, headers);
			seqW.write(row);
		}
		seqW.close();		
	}

	private static List<String> recordToRow(Record record, List<String> headers) {
		List<String> row = new ArrayList<String>();
		row.add(record.getId());
		headers.forEach(h -> {
			row.add(record.getAttributeValue(h) == null ? "" : record.getAttributeValue(h).toString());
		});
		return row;
	}
}
