@echo off

echo.
echo ==================================================
echo    Subiendo cambios a GitHub...
echo.

@copy *.* "E:\Archivos-Personales\GoogleDrive\Codigo Propio\HTML\PlantCare\"
@copy *.* "E:\Archivos-Personales\OneDrive\Archivos Privados\Codigo Propio\HTML\PlantCare\"
xcopy /E /Y /Q "icons" "E:\Archivos-Personales\GoogleDrive\Codigo Propio\HTML\PlantCare\icons\"
xcopy /E /Y /Q "icons" "E:\Archivos-Personales\OneDrive\Archivos Privados\Codigo Propio\HTML\PlantCare\icons\"

echo.
echo    Subiendo cambios a GitHub...
echo.
git add index.html
git add manifest.json
git add sw.js
git add backup.bat
git add icons

git commit -m "Deploy automatico: Actualizacion de la app PlantCare"
REM Sube los cambios a la rama principal de tu repositorio en GitHub.
git push origin HEAD

echo.
echo      PROCESO DE PUBLICACION FINALIZADO
echo ==================================================
echo.

pause