package io.javabrains.betterreads.home;

import io.javabrains.betterreads.user.BooksByUser;
import io.javabrains.betterreads.user.BooksByUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.cassandra.core.query.CassandraPageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private final String COVER_IMAGE_ROOT = "https://covers.openlibrary.org/b/id/";

    @Autowired
    private BooksByUserRepository booksByUserRepository;

    @GetMapping("/")
    public String home(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null || principal.getAttribute("login") == null) {
            return "index";
        }

        String userId = principal.getAttribute("login");
        Slice<BooksByUser> booksSlice = booksByUserRepository.findAllById(userId, CassandraPageRequest.of(0, 10));
        List<BooksByUser> booksByUser = booksSlice.getContent().stream().distinct().map(book -> {
            String coverId = book.getCoverIds().get(0);
            if (StringUtils.hasText(coverId)) {
                coverId = COVER_IMAGE_ROOT + coverId + "-M.jpg";
            } else {
                coverId = "/images/no-image.png";
            }
            book.setCoverUrl(coverId);
            book.setReadingStatus(book.getReadingStatus().substring(2));
            return book;
        }).collect(Collectors.toList());
        model.addAttribute("books", booksByUser);
        return "home";
    }
}
