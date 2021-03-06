package ro.uaic.info.ip.proiect.b3.controllers.subject.homework;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ro.uaic.info.ip.proiect.b3.configurations.ServerErrorMessages;
import ro.uaic.info.ip.proiect.b3.database.Database;
import ro.uaic.info.ip.proiect.b3.database.objects.cont.Cont;
import ro.uaic.info.ip.proiect.b3.database.objects.inscriere.Inscriere;
import ro.uaic.info.ip.proiect.b3.database.objects.inscriere.exceptions.InscriereException;
import ro.uaic.info.ip.proiect.b3.database.objects.materie.Materie;
import ro.uaic.info.ip.proiect.b3.database.objects.tema.Tema;
import ro.uaic.info.ip.proiect.b3.database.objects.temaexercitiuextensie.TemaExercitiuExtensie;
import ro.uaic.info.ip.proiect.b3.database.objects.temaincarcata.TemaIncarcata;
import ro.uaic.info.ip.proiect.b3.generators.FileNameGenerator;
import ro.uaic.info.ip.proiect.b3.permissions.PermissionManager;
import ro.uaic.info.ip.proiect.b3.storage.StorageProperties;
import ro.uaic.info.ip.proiect.b3.storage.filesystemstorage.FileSystemStorageService;

import java.sql.SQLException;
import java.util.Date;

@Controller
public class UploadHomeworkController {
    Logger logger = Logger.getLogger(UploadHomeworkController.class);

    @RequestMapping(value = "/materii/{numeMaterie}/{numeTema}/upload/{nrExercitiu}", method = RequestMethod.POST)
    public @ResponseBody
    String uploadHomework(
            @CookieValue(value = "user", defaultValue = "-1") String loginToken,
            @PathVariable("numeMaterie") String numeMaterie,
            @PathVariable("numeTema") String numeTema,
            @PathVariable("nrExercitiu") int nrExercitiu,
            @RequestParam("file") MultipartFile file) {

        try {
            if (PermissionManager.isLoggedUserStudent(loginToken)) {
                Cont cont = Cont.getByLoginToken(loginToken);
                if (cont == null) {
                    logger.error("Cont get by login token returned null after PermissionManager.isLoggedUserStudent returned true");
                    return ServerErrorMessages.INTERNAL_ERROR_MESSAGE;
                }

                Materie materie = Materie.getByTitlu(numeMaterie);
                if (materie == null) {
                    return "Materie invalida!";
                }

                try {
                    Inscriere inscriere = Inscriere.get(cont.getId(), materie.getId());
                    if (inscriere == null) return "Trebuie sa fii inscris la aceasta materie pentru a incarca teme!";
                } catch (InscriereException e) {
                    return e.getMessage();
                }

                Tema tema = Tema.getByMaterieIdAndNumeTema(materie.getId(), numeTema);
                if (tema == null) {
                    return "Tema invalida!";
                }

                TemaIncarcata temaIncarcata = TemaIncarcata.get(cont.getId(), tema.getId(), nrExercitiu);
                if (temaIncarcata != null) {
                    return "Solutie deja uploadata!";
                }

                Date deadline = tema.getDeadline();
                if (new Date().after(deadline)) {
                    return "Deadline depasit!";
                }

                /* verificam extensia.. */

                String fileName = FileNameGenerator.getNewFileName();
                new FileSystemStorageService(new StorageProperties()).store(fileName, file);

                Database.getInstance().updateOperation("INSERT into teme_incarcate (id_cont, id_tema, nr_exercitiu, nume_fisier_tema) "
                                + "VALUES (?, ?, ?, ?)"
                        , Long.toString(Cont.getByLoginToken(loginToken).getId()), Long.toString(tema.getId()), Integer.toString(nrExercitiu), fileName);

                return "valid";
            } else return ServerErrorMessages.UNAUTHORIZED_ACCESS_MESSAGE;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return ServerErrorMessages.INTERNAL_ERROR_MESSAGE;
        }
    }
}

