package io.javabrains.betterreadsdataloader;

import io.javabrains.betterreadsdataloader.author.Author;
import io.javabrains.betterreadsdataloader.author.AuthorRepository;
import io.javabrains.betterreadsdataloader.book.Book;
import io.javabrains.betterreadsdataloader.book.BookRepository;
import io.javabrains.betterreadsdataloader.connection.DataStaxAstraProperties;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CqlSessionBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cglib.core.Local;
import org.springframework.context.annotation.Bean;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootApplication
@EnableConfigurationProperties(value = DataStaxAstraProperties.class)
public class BetterreadsDataLoaderApplication {

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private BookRepository bookRepository;

    @Value("${datadump.location.author}")
    private String authorDumpLocation;

    @Value("${datadump.location.work}")
    private String workDumpLocation;

    public static void main(String[] args) {
        SpringApplication.run(BetterreadsDataLoaderApplication.class, args);
    }

    @Bean
    public CqlSessionBuilderCustomizer sessionBuilderCustomizer(DataStaxAstraProperties dataStaxAstraProperties) {
        Path bundlePath = dataStaxAstraProperties.getSecureConnectBundle().toPath();
        return cqlSessionBuilder -> cqlSessionBuilder.withCloudSecureConnectBundle(bundlePath);
    }

    @PostConstruct
    public void start() {
        initAuthors();
        initWorks();
    }

    private void initAuthors() {
        Path path = Paths.get(authorDumpLocation);

        try (Stream<String> lines = Files.lines(path)) {
            lines.forEach(line -> {
                // Read and parse line
                String jsonString = line.substring(line.indexOf('{'));
                try {
                    JSONObject jsonObject = new JSONObject(jsonString);

                    // Construct Author Object
                    Author author = new Author();
                    author.setId(jsonObject.optString("key").replace("/authors/", ""));
                    author.setName(jsonObject.optString("name"));
                    author.setPersonalName(jsonObject.optString("personal_name"));

                    // Persist using authorRepository
                    System.out.println("Saving author: " + author.getName());
                    authorRepository.save(author);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void initWorks() {
        Path path = Paths.get(workDumpLocation);

        try (Stream<String> lines = Files.lines(path)) {
            lines.forEach(line -> {
                String jsonString = line.substring(line.indexOf("{"));
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

                try {
                    JSONObject jsonObject = new JSONObject(jsonString);

                    Book book = new Book();
                    book.setId(jsonObject.optString("key").replace("/works/", ""));
                    book.setName(jsonObject.optString("title"));

                    JSONObject descriptionObj = jsonObject.optJSONObject("description");
                    if (descriptionObj != null) {
                        book.setDescription(descriptionObj.optString("value"));
                    }

                    JSONObject publishedObj = jsonObject.optJSONObject("created");
                    if (publishedObj != null) {
                        String publishedDate = publishedObj.optString("value");
                        book.setPublishedDate(LocalDate.parse(publishedDate, formatter));
                    }

                    JSONArray coversJSONArray = jsonObject.optJSONArray("covers");
                    if (coversJSONArray != null) {
                        List<String> coverIds = new ArrayList<>();
                        for (int i = 0; i < coversJSONArray.length(); i++) {
                            coverIds.add(coversJSONArray.getString(i));
                        }
                        book.setCoverIds(coverIds);
                    }

                    JSONArray authorsJSONArray = jsonObject.optJSONArray("authors");
                    if (authorsJSONArray != null) {
                        List<String> authorIds = new ArrayList<>();
                        for (int i = 0; i < authorsJSONArray.length(); i++) {
                            JSONObject authorObj = authorsJSONArray.optJSONObject(i).optJSONObject("author");
                            if (authorObj != null) {
                                String authorId = authorObj.optString("key").replace("/authors/", "");
                                authorIds.add(authorId);
                            }
                        }
                        book.setAuthorIds(authorIds);

                        List<String> authorNames = authorIds.stream().map(id -> authorRepository.findById(id)).map(optionalAuthor -> {
                            if (optionalAuthor.isEmpty()) {
                                return "Unknown Author";
                            }
                            return optionalAuthor.get().getName();
                        }).collect(Collectors.toList());
                        book.setAuthorNames(authorNames);
                    }

                    System.out.println("Saving book: " + book.getName());
                    bookRepository.save(book);

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
